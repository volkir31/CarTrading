package Seller;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class SellerAgent extends Agent {
    private Hashtable<Integer, Hashtable<Integer, Integer>> catalogue;

    private GUI myGui;

    protected void setup() {
        catalogue = new Hashtable<>();

        myGui = new GUI(this);
        myGui.showGui();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("car-selling");
        sd.setName("JADE-car-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new OfferRequestsServer());
        addBehaviour(new PurchaseOrdersServer());

    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        myGui.dispose();
        System.out.println("Seller agent " + getAID().getName() + "terminating.");
    }

    public void updateCatalogue(final int mileage, final int price) {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                Hashtable<Integer, Integer> newCar = new Hashtable<>();
                newCar.put(mileage, price);
                catalogue.put(catalogue.size() + 1, newCar);
                System.out.println("Seller agent - " + getAID().getName() + ": car inserted into catalogue. " + "Mileage = " + mileage + " by " + price);
            }
        });
    }

    public void showUnsoldCars() {
        Enumeration<Integer> keys = catalogue.keys();
        System.out.println("Unsold cars: ");

        while (keys.hasMoreElements()) {
            System.out.println("Car with mileage: " + keys.nextElement() + " sold");
        }

        System.out.println();
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String[] requirements = msg.getContent().split(" ");
                int mileageReq = Integer.parseInt(requirements[0]);
                int priceReq = Integer.parseInt(requirements[1]);
                ArrayList<String> cars = new ArrayList<String>();

                for (Integer id: catalogue.keySet()) {
                    Hashtable<Integer, Integer> params = catalogue.get(id);
                    params.forEach((mileage, price) -> {
                        if (mileage <= mileageReq && price <= priceReq) {
                            cars.add(id + " " + mileage + " " + price);
                        }
                    });
                }

                String answer = String.join(",", cars);

                ACLMessage reply = msg.createReply();

                if (answer.length() != 0) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(answer);
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                    block();
                }

                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                Integer car = Integer.parseInt(msg.getContent());
                ACLMessage reply = msg.createReply();

                Hashtable<Integer, Integer> cont = catalogue.remove(car);
                cont.forEach((mileage, price) -> {
                    if (price != null && mileage != null) {
                        reply.setPerformative(ACLMessage.INFORM);
                        System.out.println(getAID().getName() + " sold car " + car + " to agent " + msg.getSender().getName());
                        showUnsoldCars();
                    } else {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("not-available");
                    }

                    myAgent.send(reply);
                });
            } else {
                block();
            }
        }
    }
}
