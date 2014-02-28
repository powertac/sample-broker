function msgTariffStatus (ts)
  % Handles a TariffStatus message. This should do something when the status
  % is not SUCCESS.

  global pmManager

  status = ts.getStatus();
  msg = horzcat('TariffStatus: ', char(status));
  pmManager.log.info(msg);
end