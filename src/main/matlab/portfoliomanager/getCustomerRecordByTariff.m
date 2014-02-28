function record = getCustomerRecordByTariff (spec, customer)
  global pmManager

  specString = char(spec);
  if isnumeric(customer) && isempty(customer)
    % customer might be null (Java) == [] (MatLab)
    customerName = 'null';
  else
    customerName = char(customer.getName());
  end

  if ~pmManager.customerSubscriptions.isKey(specString)
    customerMap = containers.Map;
    pmManager.customerSubscriptions(specString) = customerMap;
  else
    customerMap = pmManager.customerSubscriptions(specString);
  end

  if ~customerMap.isKey(customerName)
    % seed with the generic record for this customer
    oldRecord = getCustomerRecordByPowerType(spec.getPowerType(), customer);
    record = CustomerRecord(oldRecord);
    customerMap(customerName) = record;
  else
    record = customerMap(customerName);
  end
end