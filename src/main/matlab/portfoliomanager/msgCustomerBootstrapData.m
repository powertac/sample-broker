function msgCustomerBootstrapData (cbd)
  % Handles CustomerBootstrapData by populating the customer model
  % corresponding to the given customer and power type. This gives the
  % broker a running start.

  global pmManager

  name = cbd.getCustomerName();
  type = cbd.getPowerType();
  usage = cbd.getNetUsage();

  customer = pmManager.customerRepo.findByNameAndPowerType(name, type);
  record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);

  check1 = record.subscribedPopulation;

  subs = record.subscribedPopulation;
  record.subscribedPopulation = customer.getPopulation();
  for i=1:length(usage)
    record.produceConsume(usage(i), i);
  end
  record.subscribedPopulation = subs;
end