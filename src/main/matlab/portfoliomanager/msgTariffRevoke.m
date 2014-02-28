function msgTariffRevoke (tr)
  % Handles a TariffRevoke message from the server, indicating that some
  % tariff has been revoked.

  global pmManager

  source = tr.getBroker();
  name = char(source.getUsername());
  contextName = char(pmManager.context.getBrokerUsername());

  % if it's from some other broker, we need to remove it from the
  % tariffRepo, and from the competingTariffs list
  if ~strcmp(name, contextName)
    pmManager.log.info('clear out competing tariff');

    original = pmManager.tariffRepo.findSpecificationById(tr.getTariffId());

    if isnumeric(original) && isempty(original)
      msg = horzcat('Original tariff ', int2str(tr.getTariffId()), 'not found');
      pmManager.log.warn(msg);
      return;
    end

    pmManager.tariffRepo.removeSpecification(original.getId());

    typeString = char(original.getPowerType());
    if ~pmManager.competingTariffs.isKey(typeString)
      pmManager.log.warn('Candidate list is null');
      return;
    else
      % candidates is an array with tariff specs, remove original
      candidates = pmManager.competingTariffs(typeString);
      candidates = candidates(candidates~=original);
      pmManager.competingTariffs(typeString) = candidates;
    end
  end
end