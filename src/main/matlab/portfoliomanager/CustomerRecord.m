classdef CustomerRecord < handle
  % Keeps track of customer status and usage. Usage is stored
  % per-customer-unit, but reported as the product of the per-customer
  % quantity and the subscribed population. This allows the broker to use
  % historical usage data as the subscribed population shifts.

  properties
    customer = 'None';
    subscribedPopulation = 0;
    usage = [];
    alpha = 0.3;
  end

  methods
    function obj = CustomerRecord (inp)
      global pmManager

      if strcmp(class(inp), 'org.powertac.common.CustomerInfo')
        obj.customer = inp;
        for i=1:pmManager.context.getUsageRecordLength()
          obj.usage(i) = 0;
        end
      elseif strcmp(class(inp), 'CustomerRecord')
        obj.customer = inp.customer;
        obj.usage = repmat(inp.usage, 1);
      elseif isnumeric(inp) && isempty(inp)
        obj.customer = inp;
        for i=1:pmManager.context.getUsageRecordLength()
          obj.usage(i) = 0;
        end
      end

      while length(obj.usage) < pmManager.context.getUsageRecordLength()
        obj.usage(length(obj.usage) + 1) = 0;
      end
    end

    % Adds new individuals to the count
    function signup (obj, population)
      obj.subscribedPopulation = min(obj.customer.getPopulation(), ...
                                     obj.subscribedPopulation + population);
    end

    % Removes individuals from the count
    function withdraw (obj, population)
      obj.subscribedPopulation = obj.subscribedPopulation - population;
    end

    function produceConsume (obj, kwh, rawIndex)
      % Customer produces or consumes power. We assume the kwh value is negative
      % for production, positive for consumption
      % store profile data at the given index

      global pmManager

      index = obj.getIndex(rawIndex);
      if obj.subscribedPopulation == 0.0
        kwhPerCustomer = 0;
      else
        kwhPerCustomer = kwh / obj.subscribedPopulation;
      end
      oldUsage = obj.usage(index);

      if oldUsage == 0.0
        % assume this is the first time
        obj.usage(index) = kwhPerCustomer;
      else
        % exponential smoothing
        obj.usage(index) = obj.alpha * kwhPerCustomer + (1.0 - obj.alpha) * oldUsage;
      end

      if isnumeric(obj.customer) && isempty(obj.customer)
        name = 'null';
      else
        name = char(obj.customer.getName());
      end

      msg = horzcat('consume ', num2str(kwh), ' at ', int2str(index), ...
                    ', customer ', name);
      pmManager.log.debug(msg);
    end

    function result = getUsage (obj, index)
      global pmManager

      if index < 1
        msg = horzcat('usage requested for negative index ', int2str(index));
        pmManager.log.warn(msg);
        index = 1;
      end

      result = obj.usage(obj.getIndex(index)) * obj.subscribedPopulation;
    end

    function index = getIndex (obj, rawIndex)
      global pmManager

      if strcmp(class(rawIndex), 'double')
        index = rawIndex;
      elseif strcmp(class(rawIndex), 'org.joda.time.Instant')
        diff = rawIndex.getMillis() - pmManager.timeService.getBase();
        duration = org.powertac.common.Competition.currentCompetition().getTimeslotDuration();
        index = floor(diff / duration) + 1;
      end

      index = mod(index-1, pmManager.context.getUsageRecordLength()) + 1;
    end
  end
end