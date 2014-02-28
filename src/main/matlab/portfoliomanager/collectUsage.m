function result = collectUsage (index)
  % Returns total usage for a given timeslot (represented as a simple index).

  global pmManager

  result = 0.0;
  for k1 = keys(pmManager.customerSubscriptions)
    customer_map = pmManager.customerSubscriptions(cell2mat(k1));
    for k2 = keys(customer_map)
      record = customer_map(cell2mat(k2));
      result = result + record.getUsage(index);
    end
  end
end