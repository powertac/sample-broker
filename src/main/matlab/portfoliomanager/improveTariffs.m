function improveTariffs ()
  % Checks to see whether our tariffs need fine-tuning

  global pmManager

  timeslotIndex = pmManager.timeslotRepo.currentTimeslot().getSerialNumber();

  % quick magic-number hack to inject a balancing order
  if timeslotIndex == 371
    broker = pmManager.context.getBroker();
    test = org.powertac.common.enumerations.PowerType.INTERRUPTIBLE_CONSUMPTION;
    specs = pmManager.tariffRepo.findTariffSpecificationsByBroker(broker).toArray();
    for i = 1:length(specs)
      spec = specs(i);
      if spec.getPowerType() == test
        order = org.powertac.common.msg.BalancingOrder(...
          broker, spec, 0.5, spec.getRates().get(0).getMinValue() * 0.9);
        pmManager.context.sendMessage(order);
      end
    end
  end

  % magic-number hack to supersede a tariff
  if timeslotIndex == 380
    broker = pmManager.context.getBroker();
    test = org.powertac.common.enumerations.PowerType.CONSUMPTION;

    % find the existing CONSUMPTION tariff
    oldc = [];
    candidates = pmManager.tariffRepo.findTariffSpecificationsByBroker(broker).toArray();
    if isnumeric(candidates) || isempty(candidates) % Test for null
      pmManager.log.error('No tariffs found for broker');
    else
      for i = 1:length(candidates)
        candidate = candidates(i);
        if candidate.getPowerType() == test
          oldc = candidate;
          break;
        end
      end

      if oldc == []
        pmManager.log.warn('No CONSUMPTION tariffs found');
      else
        rateValue = oldc.getRates().get(0).getValue();
        % create a new CONSUMPTION tariff
        spec = org.powertac.common.TariffSpecification(broker, test). ...
          withPeriodicPayment(pmManager.defaultPeriodicPayment * 1.1);
        rate = org.powertac.common.Rate().withValue(rateValue);
        spec.addRate(rate);
        spec.addSupersedes(oldc.getId());

        pmManager.tariffRepo.addSpecification(spec);
        pmManager.context.sendMessage(spec);
        % revoke the old one
        revoke = org.powertac.common.msg.TariffRevoke(broker, oldc);
        pmManager.context.sendMessage(revoke);
      end
    end
  end

%  % magic-number hack to supersede a tariff
%  if timeslotIndex == 380
%    broker = pmManager.context.getBroker();
%    type = org.powertac.common.enumerations.PowerType.CONSUMPTION;
%
%    % find the existing CONSUMPTION tariff
%    oldc = [];
%    candidates = pmManager.tariffRepo.findTariffSpecificationsByPowerType(type);
%    if isnumeric(candidates) || isempty(candidates) % Test for null
%      pmManager.log.error('No CONSUMPTION tariffs found');
%    end
%    candidates = candidates.toArray();
%    oldc = candidates(1);
%
%    rateValue = oldc.getRates().get(0).getValue();
%
%    % create a new CONSUMPTION tariff
%    spec = org.powertac.common.TariffSpecification(broker, type). ...
%      withPeriodicPayment(pmManager.defaultPeriodicPayment * 1.1);
%    rate = org.powertac.common.Rate().withValue(rateValue);
%
%    if ~isnumeric(oldc) && ~isempty(oldc)
%      spec.addSupersedes(oldc.getId());
%    end
%
%    pmManager.tariffRepo.addSpecification(spec);
%    pmManager.context.sendMessage(spec);
%    % revoke the old one
%    revoke = org.powertac.common.msg.TariffRevoke(broker, oldc);
%    pmManager.context.sendMessage(revoke);
%  end
end
