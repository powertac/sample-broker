function msgTariffTransaction (ttx)
  % Handles a TariffTransaction. We only care about certain types:
  % PRODUCE, CONSUME, SIGNUP, and WITHDRAW.

  global pmManager

  txTypeString = char(ttx.getTxType());
  newSpec = ttx.getTariffSpec();

  % make sure we have this tariff
  if ~isa(newSpec, 'org.powertac.common.TariffSpecification')
    msg = horzcat('TariffTransaction type=',  txTypeString, ' for unknown spec');
    pmManager.log.error(msg);
  else
    oldSpec = pmManager.tariffRepo.findSpecificationById(newSpec.getId());
    if oldSpec.getId() ~= newSpec.getId()
      msg = horzcat('Incoming spec ', num2str(newSpec.getId()), ...
        ' not matched in repo');
      pmManager.log.error(msg);
    end
  end

  % ttx.getCustomerInfo() might return null => [] in MatLab
  record = getCustomerRecordByTariff(newSpec, ttx.getCustomerInfo());

  enumType = 'org.powertac.common.TariffTransaction$Type';
  SIGNUP   = javaMethod('valueOf', enumType, 'SIGNUP');
  WITHDRAW = javaMethod('valueOf', enumType, 'WITHDRAW');
  PRODUCE  = javaMethod('valueOf', enumType, 'PRODUCE');
  PUBLISH  = javaMethod('valueOf', enumType, 'PUBLISH');

  if strcmp(txTypeString, char(SIGNUP))
    % keep track of customer counts
    record.signup(ttx.getCustomerCount());
  elseif strcmp(txTypeString, char(WITHDRAW))
    % customers presumably found a better deal
    record.withdraw(ttx.getCustomerCount());
  elseif strcmp(txTypeString, char(PRODUCE))
    % if ttx count and subscribe population don't match, it will be hard
    % to estimate per-individual production
    if ttx.getCustomerCount() ~= record.subscribedPopulation
      msg = horzcat('production by subset ', num2str(ttx.getCustomerCount()),...
        ' of subscribed population ', int2str(record.subscribedPopulation));
      pmManager.log.warn(msg);
    end
    record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
  elseif strcmp(txTypeString, char(PUBLISH))
    if ttx.getCustomerCount() ~= record.subscribedPopulation
      msg = horzcat('production by subset ', num2str(ttx.getCustomerCount()),...
        ' of subscribed population ', int2str(record.subscribedPopulation));
      pmManager.log.warn(msg);
    end
    record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
  end
end
