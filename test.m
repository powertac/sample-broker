% Run this with : matlab -nodisplay -nosplash -nodesktop -r test

javaaddpath('target/sample-broker.jar');

o = org.powertac.samplebroker.core.BrokerMain;
javaMethod('mainMatlab', o, '--jms-url=tcp://130.115.197.116:61616');
