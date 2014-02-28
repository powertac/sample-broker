function msgTariffSpecification (spec)
  % Handles a TariffSpecification. These are sent by the server when new tariffs
  % are published. If it's not ours, then it's a competitor's tariff. We keep
  % track of competing tariffs locally, and we also store them in the tariffRepo.

  global pmManager

  theBroker = spec.getBroker();
  theBrokerName = char(theBroker.getUsername());
  contextBrokerName = char(pmManager.context.getBrokerUsername());

  if strcmp(theBrokerName, contextBrokerName)
    % if it's ours, just log it, because we already put it in the repo
    original = pmManager.tariffRepo.findSpecificationById(spec.getId());
    if ~isa(original, 'org.powertac.common.TariffSpecification')
      pmManager.log.error(horzcat('Spec ', int2str(spec.getId()), ' not in local repo'));
    end
    pmManager.log.info(horzcat('published ', char(spec.toString())));
  else
    % otherwise, keep track of competing tariffs, and record in the repo
    addCompetingTariff(spec);
    pmManager.tariffRepo.addSpecification(spec);
  end
end

function addCompetingTariff (spec)
  % Finds the list of competing tariffs for the given PowerType, add tariff
  global pmManager

  typeString = char(spec.getPowerType());

  if ~pmManager.competingTariffs.isKey(typeString)
    tariffs = [];
  else
    tariffs = pmManager.competingTariffs(typeString);
  end

  tariffs = [tariffs spec];
  pmManager.competingTariffs(typeString) = tariffs;
end