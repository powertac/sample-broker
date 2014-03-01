from org.powertac.common.enumerations import PowerType
from org.powertac.common import TariffTransaction, TariffSpecification, Rate, \
    Competition
from org.powertac.common.msg import BalancingOrder, TariffRevoke, \
    CustomerBootstrapData, TariffStatus, BalancingControlEvent
from org.powertac.samplebroker.interfaces import PortfolioManagerJython


class PortfolioManagerService (PortfolioManagerJython):
    """Handles portfolio-management responsibilities for the broker. This
    includes composing and offering tariffs, keeping track of customers and
    their usage, monitoring tariff offerings from competing brokers.

    A more complete broker implementation might split this class into two or
    more classes; the keys are to decide which messages each class handles,
    what each class does on the activate() method, and what data needs to be
    managed and shared.

    @author John Collins, Govert Buijs
    """

    def __init__ (self):
        self.default_margin = 0.5
        self.fixed_per_kwh = -0.06
        self.default_periodic_payment = -1.0

    def init (self, context, timeslot_repo, tariff_repo,
              customer_repo, market_manager, time_service, log):

        self.broker_context = context
        self.timeslot_repo  = timeslot_repo
        self.tariff_repo    = tariff_repo
        self.customer_repo  = customer_repo
        self.market_manager = market_manager
        self.time_service   = time_service
        self.log            = log

        # ---- Portfolio records -----
        # Customer records indexed by power type and by tariff. Note that the
        # CustomerRecord instances are NOT shared between these structures,
        # because we need to keep track of subscriptions by tariff.
        self.customer_profiles = {}
        self.customer_subscriptions = {}
        self.competing_tariffs = {}

    # ---- These are only used here, 'private' ----
    def _get_record_by_powertype (self, type, customer):
        customer_map = self.customer_profiles.get(type)
        if customer_map == None:
            customer_map = {}
            self.customer_profiles[type] = customer_map

        record = customer_map.get(customer)
        if record == None:
            record = CustomerRecord(self.log, self.broker_context,
                                    self.time_service, customer, None)
            customer_map[customer] = record

        return record

    def _get_record_by_tariff (self, spec, customer):
        customer_map = self.customer_subscriptions.get(spec)
        if customer_map == None:
            customer_map = {}
            self.customer_subscriptions[spec] = customer_map

        record = customer_map.get(customer)
        if record == None:
            # seed with the generic record for this customer
            old_record = self._get_record_by_powertype(spec.getPowerType(),
                                                      customer)
            record = CustomerRecord(self.log, self.broker_context,
                                    self.time_service, None, old_record)
            customer_map[customer] = record

        return record

    def _get_competing_tariffs (self, powerType):
        """Finds the list of competing tariffs for the given PowerType.
        """
        result = self.competing_tariffs.get(powerType)
        if result == None:
            result = []
            self.competing_tariffs[powerType] = result

        return result

    def _add_competing_tariff (self, spec):
        """Adds a new competing tariff to the list.
        """
        self._get_competing_tariffs(spec.getPowerType()).append(spec)

    def collectUsage (self, index):
        """Returns total usage for a given timeslot (represented as a simple index).
        """
        result = 0.0
        for customer_map in self.customer_subscriptions.values():
            for record in customer_map.values():
                result += record.get_usage(index)

        # convert to needed energy account balance
        return -result

    # -------------- Message handlers -------------------
    def handleMessage (self, message):
        if type(message) == CustomerBootstrapData:
            self._handle_message_CustomerBootstrapData(message)
        elif type(message) == TariffSpecification:
            self._handle_message_TariffSpecification(message)
        elif type(message) == TariffStatus:
            self._handle_message_TariffStatus(message)
        elif type(message) == TariffTransaction:
            self._handle_message_TariffTransaction(message)
        elif type(message) == TariffRevoke:
            self._handle_message_TariffRevoke(message)
        elif type(message) == BalancingControlEvent:
            self._handle_message_BalancingControlEvent(message)

    def _handle_message_CustomerBootstrapData (self, cbd):
        """Handles CustomerBootstrapData by populating the customer model
        corresponding to the given customer and power type. This gives the
        broker a running start.
        """
        customer = self.customer_repo.findByNameAndPowerType(
            cbd.getCustomerName(), cbd.getPowerType())
        record = self._get_record_by_powertype(cbd.getPowerType(), customer)
        subs = record.subscribed_population
        record.subscribed_population = customer.getPopulation()
        for i in range(len(cbd.getNetUsage())):
            record.produce_consume(cbd.getNetUsage()[i], i)
        record.subscribed_population = subs

    def _handle_message_TariffSpecification (self, spec):
        """Handles a TariffSpecification.
        These are sent by the server when new tariffs are published.
        If it's not ours, then it's a competitor's tariff.
        We keep track of competing tariffs locally,
        and we also store them in the tariffRepo.
        """
        the_broker = spec.getBroker()
        if self.broker_context.getBrokerUsername() == the_broker.getUsername():
            if the_broker != self.broker_context:
                # strange bug, seems harmless for now
                self.log.info("Resolution failed for broker %s" %
                              the_broker.getUsername())
            # if it's ours, just log it, because we already put it in the repo
            original = self.tariff_repo.findSpecificationById(spec.getId())
            if original == None:
                self.log.error("Spec %s not in local repo" % spec.getId())
            self.log.info("published %s" % spec)

        else:
            # otherwise, keep track of competing tariffs, and record in the repo
            self._add_competing_tariff(spec)
            self.tariff_repo.addSpecification(spec)

    def _handle_message_TariffStatus (self, ts):
        """ Handles a TariffStatus message.
        This should do something when the status is not SUCCESS.
        """
        self.log.info("TariffStatus: %s" % ts.getStatus())

    def _handle_message_TariffTransaction (self, ttx):
        """ Handles a TariffTransaction.
        We only care about certain types: PRODUCE, CONSUME, SIGNUP, and WITHDRAW
        """
        #make sure we have this tariff
        new_spec = ttx.getTariffSpec()
        if new_spec == None:
            self.log.error("TariffTransaction type = %s for unknown spec" %
                           ttx.getTxType())
        else:
            oldSpec = self.tariff_repo.findSpecificationById(new_spec.getId())
            if oldSpec != new_spec:
                self.log.error("Incoming spec %s not matched in repo" %
                               new_spec.getId())
        ttx_type = ttx.getTxType()
        record = self._get_record_by_tariff(ttx.getTariffSpec(),
                                           ttx.getCustomerInfo())

        if ttx_type == TariffTransaction.Type.SIGNUP:
            # keep track of customer counts
            record.signup(ttx.getCustomerCount())
        elif ttx_type == TariffTransaction.Type.WITHDRAW:
            # customers presumably found a better deal
            record.withdraw(ttx.getCustomerCount())
        elif ttx_type == TariffTransaction.Type.PRODUCE:
            # if ttx count and subscribe population don't match, it will be hard
            # to estimate per-individual production
            if ttx.getCustomerCount() != record.subscribed_population:
                self.log.warn(
                    "production by subset %s of subscribed population %s" %
                    (ttx.getCustomerCount(), record.subscribed_population))
            record.produce_consume_from_Instant(ttx.getKWh(), ttx.getPostedTime())
        elif ttx_type == TariffTransaction.Type.CONSUME:
            if ttx.getCustomerCount() != record.subscribed_population:
                self.log.warn(
                    "consumption by subset %s of subscribed population %s" %
                    (ttx.getCustomerCount(), record.subscribed_population))
            record.produce_consume_from_Instant(ttx.getKWh(), ttx.getPostedTime())

    def _handle_message_TariffRevoke (self, tr):
        """ Handles a TariffRevoke message from the server, indicating that some
        tariff has been revoked.
        """
        source = tr.getBroker()
        self.log.info("Revoke tariff %s" % tr.getTariffId()
                 + " from " + tr.getBroker().getUsername())

        # if it's from some other broker, we need to remove it from the
        # tariffRepo, and from the competingTariffs list
        if source.getUsername() != self.broker_context.getBrokerUsername():
            self.log.info("clear out competing tariff")
            original = self.tariff_repo.findSpecificationById(tr.getTariffId())
            if original == None:
                self.log.warn("Original tariff %s not found" % tr.getTariffId())
                return
            self.tariff_repo.removeSpecification(original.getId())
            candidates = self.competing_tariffs.get(original.getPowerType())
            if candidates == None:
                self.log.warn("Candidate list is null")
                return
            candidates.remove(original)

    def _handle_message_BalancingControlEvent (self, bce):
        """ Handles a BalancingControlEvent, sent when a BalancingOrder is
        exercised by the DU.
        """
        self.log.info("BalancingControlEvent %s" % bce.getKwh())

    #--------------- activation -----------------
    def activate (self, timeslotIndex):
        """Called after TimeslotComplete msg received. Note that activation
        order among modules is non-deterministic.
        """
        if self.customer_subscriptions == {}:
            # we (most likely) have no tariffs
            self._create_initial_tariffs()
        else:
            # we have some, are they good enough?
            self._improve_tariffs()

    def _create_initial_tariffs (self):
        """Creates initial tariffs for the main power types. These are simple
        fixed-rate two-part tariffs that give the broker a fixed margin.
        """
        # remember that market prices are per mwh, but tariffs are by kwh
        market_price = self.market_manager.getMeanMarketPrice() / 1000.0
        # for each power type representing a customer population,
        # create a tariff that's better than what's available
        for pt in self.customer_profiles.keys():
            # we'll just do fixed-rate tariffs for now
            if pt.isConsumption():
                rate_value = ((market_price + self.fixed_per_kwh) *
                              (1.0 + self.default_margin))
            else:
                # rateValue = (-1.0 * marketPrice / (1.0 + defaultMargin))
                rate_value = -2.0 * market_price
            if pt.isInterruptible():
                rate_value *= 0.7 # Magic number!! price break for interruptible
            spec = TariffSpecification(self.broker_context.getBroker(), pt)\
                .withPeriodicPayment(self.default_periodic_payment)
            rate = Rate().withValue(rate_value)

            if pt.isInterruptible():
                # set max curtailment
                rate.withMaxCurtailment(0.1)

            spec.addRate(rate)
            self.customer_subscriptions[spec] = {}
            self.tariff_repo.addSpecification(spec)
            self.broker_context.sendMessage(spec)

    def _improve_tariffs_todo(self):
        """Checks to see whether our tariffs need fine-tuning
        """
        # quick magic-number hack to inject a balancing order
        timeslot_index = self.timeslot_repo.currentTimeslot().getSerialNumber()
        if timeslot_index == 371:
            for spec in self.tariff_repo.findTariffSpecificationsByBroker(
                    self.broker_context.getBroker()):
                if PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType():
                    order = BalancingOrder(self.broker_context.getBroker(),
                                           spec,
                                           0.5,
                                           spec.getRates().get(0).getMinValue() * 0.9)
                    self.broker_context.sendMessage(order)

        # magic-number hack to supersede a tariff
        if timeslot_index == 380:
            # find the existing CONSUMPTION tariff
            oldc = None
            candidates = self.tariff_repo.findTariffSpecificationsByPowerType(
                PowerType.CONSUMPTION)
            if candidates == None or  len(candidates) == 0:
                self.log.error("No CONSUMPTION tariffs found")
            else:
                oldc = candidates.get(0)

            rate_value = oldc.getRates().get(0).getValue()
            # create a new CONSUMPTION tariff
            spec = TariffSpecification(self.broker_context.getBroker(),
                                       PowerType.CONSUMPTION)\
                .withPeriodicPayment(self.default_periodic_payment * 1.1)
            rate = Rate().withValue(rate_value)
            spec.addRate(rate)
            if oldc != None:
                spec.addSupersedes(oldc.getId())
            self.tariff_repo.addSpecification(spec)
            self.broker_context.sendMessage(spec)
            # revoke the old one
            revoke = TariffRevoke(self.broker_context.getBroker(), oldc)
            self.broker_context.sendMessage(revoke)

    def _improve_tariffs(self):
        """Checks to see whether our tariffs need fine-tuning
        """
        broker = self.broker_context.getBroker()

        # quick magic-number hack to inject a balancing order
        timeslot_index = self.timeslot_repo.currentTimeslot().getSerialNumber()
        if timeslot_index == 371:
            for spec in self.tariff_repo.\
                    findTariffSpecificationsByBroker(broker):
                if PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType():
                    order = BalancingOrder(self.broker_context.getBroker(),
                                           spec,
                                           0.5,
                                           spec.getRates().get(0).getMinValue() * 0.9)
                    self.broker_context.sendMessage(order)

        # magic-number hack to supersede a tariff
        if timeslot_index == 380:
            # find the existing CONSUMPTION tariff
            oldc = None
            candidates = self.tariff_repo.\
                findTariffSpecificationsByBroker(broker)

            if candidates == None or len(candidates) == 0:
                self.log.error("No tariffs found for broker")
            else:
                for candidate in candidates:
                    if candidate.getPowerType() == PowerType.CONSUMPTION:
                        oldc = candidate
                        break

                if oldc == None:
                    self.log.warn("No CONSUMPTION tariffs found")
                else:
                    rateValue = oldc.getRates().get(0).getValue()
                    # create a new CONSUMPTION tariff
                    spec = TariffSpecification(broker, PowerType.CONSUMPTION) \
                        .withPeriodicPayment(self.default_periodic_payment * 1.1)
                    rate = Rate().withValue(rateValue)
                    spec.addRate(rate)

                    if oldc == None:
                        spec.addSupersedes(oldc.getId())
                    self.tariff_repo.addSpecification(spec)
                    self.broker_context.sendMessage(spec)

                    # revoke the old one
                    revoke = TariffRevoke(broker, oldc)
                    self.broker_context.sendMessage(revoke)


class CustomerRecord ():
    def __init__ (self, log, context, time_service, customer, old_record):
        self.subscribed_population = 0
        self.alpha = 0.3
        self.log = log
        self.time_service = time_service
        self.customer = None
        self.usage = [0.0] * context.getUsageRecordLength()

        if customer != None:
            self.customer = customer
        elif old_record != None:
            self.customer = old_record.customer
            self.usage = old_record.usage[:] + (
                [0.0]*(context.getUsageRecordLength() - len(old_record.usage)))

    def get_customer_info (self):
        """Returns the CustomerInfo for this record
        """
        return self.customer

    def signup (self, population):
        """Adds new individuals to the count
        """
        self.subscribed_population = min(self.customer.getPopulation(),
                                         self.subscribed_population+population)

    def withdraw (self, population):
        """Removes individuals from the count
        """
        self.subscribed_population -= population

    def produce_consume_from_Instant (self, kwh, when):
        """Customer produces or consumes power. We assume the kwh value is
        negative for production, positive for consumption
        """
        index = self._get_index_from_Instant(when)
        self.produce_consume(kwh, index)

    def produce_consume (self, kwh, raw_index):
        """store profile data at the given index
        """
        index = self._get_index(raw_index)
        kwhPerCustomer = kwh / (1.0 * self.subscribed_population)
        oldUsage = self.usage[index]
        if oldUsage == 0.0:
            # assume this is the first time
            self.usage[index] = kwhPerCustomer
        else:
            # exponential smoothing
            self.usage[index] = self.alpha * kwhPerCustomer + \
                                (1.0 - self.alpha) * oldUsage
        self.log.debug("consume %s at %s, customer %s" %
                       (kwh, index, self.customer.getName()))

    def get_usage (self, index):
        if index < 0:
            self.log.warn("usage requested for negative index %s" % index)
            index = 0
        return int(self.usage[self._get_index(index)]*self.subscribed_population)

    def _get_index_from_Instant (self, when):
        """we assume here that timeslot index always matches the number of
        timeslots that have passed since the beginning of the simulation.
        """
        result = when.getMillis() - self.time_service.getBase() / \
                                    Competition.currentCompetition().getTimeslotDuration()
        return int(result)

    def _get_index (self, raw_index):
        return raw_index % len(self.usage)