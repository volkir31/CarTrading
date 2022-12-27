package buyer

import jade.core.AID
import jade.core.Agent
import jade.core.behaviours.Behaviour
import jade.core.behaviours.TickerBehaviour
import jade.domain.DFService
import jade.domain.FIPAAgentManagement.DFAgentDescription
import jade.domain.FIPAAgentManagement.ServiceDescription
import jade.domain.FIPAException
import jade.lang.acl.ACLMessage
import jade.lang.acl.MessageTemplate

class Buyer : Agent() {
    private var purchasedCar = 0
    private var priceRequirement = 0
    private var mileageRequirement = 0
    private var maxAttempt = 0

    private lateinit var sellerAgents: Array<AID?>

    override fun setup() {
        val args = arguments
        if (args != null && args.isNotEmpty()) {
            val cont = args[0].toString().split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            mileageRequirement = cont[0].toInt()
            priceRequirement = cont[1].toInt()

            addBehaviour(object : TickerBehaviour(this, 10000) {
                override fun onTick() {
                    println("Trying to buy car with mileage less or equal $mileageRequirement and price less or equal $priceRequirement")
                    val template = DFAgentDescription()
                    val sd = ServiceDescription()
                    sd.type = "car-selling"
                    template.addServices(sd)

                    try {
                        val result = DFService.search(myAgent, template)
                        println("Found the following seller agents:")
                        sellerAgents = arrayOfNulls(result.size)
                        result.indices.forEach {
                            sellerAgents[it] = result[it].name
                        }
                    } catch (fe: FIPAException) {
                        fe.printStackTrace()
                    }

                    myAgent.addBehaviour(object : Behaviour() {
                        private var bestSeller: AID? = null
                        private var bestPrice = 0
                        private var bestMileage = 0
                        private var repliesCnt = 0
                        private var mt: MessageTemplate? = null
                        private var step = 0

                        override fun action() {
                            when (step) {
                                0 -> {
                                    val cfp = ACLMessage(ACLMessage.CFP)

                                    sellerAgents.forEach {
                                        cfp.addReceiver(it)
                                    }

                                    cfp.content = "$mileageRequirement $priceRequirement"
                                    cfp.conversationId = "car-trade"
                                    cfp.replyWith = "cfp" + System.currentTimeMillis()
                                    myAgent.send(cfp)
                                    mt = MessageTemplate.and(
                                        MessageTemplate.MatchConversationId("car-trade"),
                                        MessageTemplate.MatchInReplyTo(cfp.replyWith)
                                    )
                                    step++
                                }

                                1 -> {
                                    val reply = myAgent.receive(mt)

                                    if (reply != null) {
                                        if (reply.performative == ACLMessage.PROPOSE) {
                                            val cars = reply.content.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                                .toTypedArray()

                                            cars.forEach { car ->
                                                val conditions = car.split(" ".toRegex()).toTypedArray()
                                                val id = conditions[0].toInt()
                                                val mileage = conditions[1].toInt()
                                                val price = conditions[2].toInt()
                                                if (bestSeller == null || mileage < bestMileage || price <= bestPrice) {
                                                    bestSeller = reply.sender
                                                    purchasedCar = id
                                                    bestMileage = mileage
                                                    bestPrice = price
                                                }
                                            }
                                        } else if (reply.performative == ACLMessage.REFUSE) {
                                            maxAttempt++
                                        }
                                        repliesCnt++
                                        if (repliesCnt >= sellerAgents.size) {
                                            if (maxAttempt >= sellerAgents.size) {
                                                println("Attempt failed: requested car is not available for sale")
                                                myAgent.doDelete()
                                            } else {
                                                step = 2
                                            }
                                        }
                                    } else {
                                        block()
                                    }
                                }

                                2 -> {
                                    val order = ACLMessage(ACLMessage.ACCEPT_PROPOSAL)
                                    order.addReceiver(bestSeller)
                                    order.content = purchasedCar.toString()
                                    order.conversationId = "car-trade"
                                    order.replyWith = "order ${System.currentTimeMillis()}"
                                    myAgent.send(order)
                                    mt = MessageTemplate.and(
                                        MessageTemplate.MatchConversationId("car-trade"),
                                        MessageTemplate.MatchInReplyTo(order.replyWith)
                                    )
                                    step = 3
                                }

                                3 -> {
                                    val reply = myAgent.receive(mt)
                                    if (reply != null) {
                                        if (reply.performative == ACLMessage.INFORM) {
                                            println("$purchasedCar successfully purchased.")
                                            println("Mileage = $bestMileage")
                                            println("Price = $bestPrice")
                                            myAgent.doDelete()
                                        } else {
                                            println("Attempt failed: requested car already sold.")
                                        }
                                        step = 4
                                    } else {
                                        block()
                                    }
                                }
                            }
                        }

                        override fun done(): Boolean {
                            if (step == 2 && bestSeller == null) {
                                println("Attempt failed: requested car is not available for sale")
                            }
                            return step == 2 && bestSeller == null || step == 4
                        }
                    })
                }
            })
        } else {
            println("No car requirements specified")
            doDelete()
        }
    }

    override fun takeDown() {
        // Printout a dismissal message
        println("Buyer - agent ${aid.name} terminating.")
    }

}