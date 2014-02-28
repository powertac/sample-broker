function msgBalancingControlEvent (bce)
  % Handles a BalancingControlEvent, sent when a BalancingOrder is
  % exercised by the DU.

  global pmManager

  kwh = bce.getKwh();
  msg = horzcat('BalancingControlEvent ', num2str(kwh));
  pmManager.log.info(msg);
end