function createInitialTariffs ()
  % Creates initial tariffs for the main power types. These are simple
  % fixed-rate two-part tariffs that give the broker a fixed margin.

  global pmManager

  % remember that market prices are per mwh, but tariffs are by kwh
  marketPrice = pmManager.marketManager.getMeanMarketPrice() / 1000;

  % for each power type representing a customer population,
  % create a tariff that's better than what's available
  for ptString = keys(pmManager.customerProfiles)
    pt = org.powertac.common.enumerations.PowerType.valueOf(cell2mat(ptString));

    % we'll just do fixed-rate tariffs for now
    if pt.isConsumption()
      rateValue = (marketPrice + pmManager.fixedPerKwh) * ...
                  (1.0 + pmManager.defaultMargin);
    else
      rateValue = -2.0 * marketPrice;
    end

    if pt.isInterruptible()
      % Magic number!! price break for interruptible
      rateValue = rateValue * 0.7;
    end

    broker = pmManager.context.getBroker();
    spec = org.powertac.common.TariffSpecification(broker, pt). ...
      withPeriodicPayment(pmManager.defaultPeriodicPayment);

    rate = org.powertac.common.Rate().withValue(rateValue);

    if pt.isInterruptible()
      % set max curtailment
      rate.withMaxCurtailment(0.1);
    end

    spec.addRate(rate);
    pmManager.customerSubscriptions(char(spec)) = containers.Map;
    pmManager.tariffRepo.addSpecification(spec);
    pmManager.context.sendMessage(spec);
  end
end