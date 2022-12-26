package Buyer;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


public class BuyerAgent extends Agent {
    private int purchasedCar;
    private int priceRequirement;
    private int mileageRequirement;
    private int maxAttempt;

    private AID[] sellerAgents;

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String[] cont = args[0].toString().split(" ");
            mileageRequirement = Integer.parseInt(cont[0]);
            priceRequirement = Integer.parseInt(cont[1]);
            addBehaviour(new TickerBehaviour(this, 10000) {
                @Override
                protected void onTick() {
                    System.out.println("Trying to buy car with mileage less or equal " + mileageRequirement + " and price less or equal " + priceRequirement);
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("car-selling");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following seller agents:");
                        sellerAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            sellerAgents[i] = result[i].getName();
                            System.out.println(sellerAgents[i].getName());
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    myAgent.addBehaviour(new RequestPerformer());
                }
            });
        } else {
            System.out.println("No car requirements specified");
            doDelete();
        }
    }

    protected void takeDown() {
// Printout a dismissal message
        System.out.println("Buyer - agent " + getAID().getName() + "terminating.");
    }

    private class RequestPerformer extends Behaviour {
        private AID bestSeller;
        private int bestPrice;
        private int bestMileage;
        private int repliesCnt = 0;
        private MessageTemplate mt;
        private int step = 0;


        public void action() {
            switch (step) {
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (AID sellerAgent : sellerAgents) {
                        cfp.addReceiver(sellerAgent);
                    }
                    cfp.setContent(mileageRequirement + " " + priceRequirement);
                    cfp.setConversationId("car-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("car-trade"), MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            String[] cars = reply.getContent().split(",");
                            for (String car : cars) {
                                String[] cont = car.split(" ");
                                int id = Integer.parseInt(cont[0]);
                                int mileage = Integer.parseInt(cont[1]);
                                int price = Integer.parseInt(cont[2]);
                                if (bestSeller == null || (mileage < bestMileage) || (price <= bestPrice)) {
                                    bestSeller = reply.getSender();
                                    purchasedCar = id;
                                    bestMileage = mileage;
                                    bestPrice = price;
                                }
                            }
                        } else if (reply.getPerformative() == ACLMessage.REFUSE) {
                            maxAttempt++;
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            if (maxAttempt >= sellerAgents.length) {
                                System.out.println("Attempt failed: requested car is not available for sale");
                                myAgent.doDelete();
                            } else {
                                step = 2;
                            }
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(String.valueOf(purchasedCar));
                    order.setConversationId("car-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("car-trade"), MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println(purchasedCar + " successfully purchased.");
                            System.out.println("Mileage = " + bestMileage);
                            System.out.println("Price = " + bestPrice);
                            myAgent.doDelete();
                        } else {
                            System.out.println("Attempt failed: requested car already sold.");
                        }
                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("Attempt failed: requested car is not available for sale");
            }

            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }
}