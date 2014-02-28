function mInit (var0, var1, var2, var3, var4, var5, var6, var7)
  global pmManager

  pmManager.context        = var0;
  pmManager.timeslotRepo   = var1;
  pmManager.tariffRepo     = var2;
  pmManager.customerRepo   = var3;
  pmManager.marketManager  = var4;
  pmManager.timeService    = var5;
  pmManager.log            = var6;
  pmManager.null           = var7;

  % Configurable parameters for tariff composition
  pmManager.defaultMargin          =  0.5;
  pmManager.fixedPerKwh            = -0.06;
  pmManager.defaultPeriodicPayment = -1.0;

  % ---- Portfolio records -----
  % Customer records indexed by power type and by tariff. Note that the
  % CustomerRecord instances are NOT shared between these structures, because
  % we need to keep track of subscriptions by tariff.
  pmManager.customerProfiles       = containers.Map;
  pmManager.customerSubscriptions  = containers.Map;
  pmManager.competingTariffs       = containers.Map;
end