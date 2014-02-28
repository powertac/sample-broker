function activate (timeslotIndex)
  % Called after TimeslotComplete msg received. Note that activation order
  % among modules is non-deterministic.

  global pmManager

  if length(pmManager.customerSubscriptions) == 0
    % we (most likely) have no tariffs
    createInitialTariffs();
  else
    % we have some, are they good enough?
    improveTariffs();
  end

end
