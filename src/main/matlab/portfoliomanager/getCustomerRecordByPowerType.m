function record = getCustomerRecordByPowerType (powerType, customer)
  global pmManager

  typeString = char(powerType);
  % customerName = char(customer.getName());
  if isnumeric(customer) && isempty(customer)
    % customer might be null (Java) == [] (MatLab)
    customerName = 'null';
  else
    customerName = char(customer.getName());
  end

  if ~pmManager.customerProfiles.isKey(typeString)
    customerMap = containers.Map;
    pmManager.customerProfiles(typeString) = customerMap;
  else
    customerMap = pmManager.customerProfiles(typeString);
  end

  if ~customerMap.isKey(customerName)
    record = CustomerRecord(customer);
    customerMap(customerName) = record;
  else
    record = customerMap(customerName);
  end
end
