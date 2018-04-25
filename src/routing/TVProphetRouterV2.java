package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import routing.util.RoutingInfo;
import util.Tuple;

public class TVProphetRouterV2 extends ActiveRouter {

	/** delivery predictability initialization constant*/
	public static final double PEncMax = 0.5;
	/** typical interconnection time in seconds*/
	public static final double I_TYP = 1800;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.9;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.999885791;
	Random randomGenerator = new Random();

	/** Prophet router's setting namespace ({@value})*/
	public static final String PROPHET_NS = "TVProphetRouterV2";
	public static  final String MESSAGE_WEIGHT = "messageWeight";
	
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";

	/*
	 * TV_PROPHET CONSTANTS
	 */
	public static final double T_OLD = 20; //seconds
	public static final double V_OLD = 100; //KBps
	public static final double TV_ALPHA = 0.5;
	public static final double TV_GAMMA = 0.98;
	
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	
	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** transmission time preds */
	private Map<DTNHost, Double> transmissionPreds; 
	/** last encouter timestamp (sim)time */
	private Map<DTNHost, Double> lastEncouterTime;

	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	/** historical connection duration */
	private Map<DTNHost, Double> tava;
	/**historical transmission duration */
	private Map<DTNHost, Double> vava;
	
	private static double indexSize;
	private static double transferRate = 100; //KBps. bluetooth
	private static double connectionDuration = 60; //seconds. distribution of connection duration
	private static double startTime;
	private static double endTime;
	
	private double transSize; 
	
	public TVProphetRouterV2(Settings s) {
		super(s);
		Settings prophetSettings = new Settings(PROPHET_NS);
		secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
		if (prophetSettings.contains(BETA_S)) {
			beta = prophetSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		System.out.println("TVPROPHETV2");
		initPreds();
		initEncTimes();
		initTava();
		initVava();
		initTransmissionPreds();

	}
	
	/**
	 * Copyc onstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected TVProphetRouterV2(TVProphetRouterV2 r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initPreds();
		initEncTimes();
		initTava();
		initVava();
		initTransmissionPreds();
	}
	
	/*
	 * TV IMPLEMENTATION STARTS HERE
	 */
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
//			updateDeliveryPredFor(otherHost);
//			updateTransitivePreds(otherHost);
			
			//add transitive sizes per host here
			
			startTime = SimClock.getTime();
//			updateTransmissionPreds(otherHost);
		}		
		else{
			DTNHost otherHost = con.getOtherNode(getHost()); //this host is same with the host in up()
			
			endTime = SimClock.getTime();
		
			double duration = endTime-startTime;
//			updateTava(otherHost, duration);
//			updateVava(otherHost, con.getSpeed());
		}
		
	}
	
	public Message getFirstMessageOnBuffer(){
		List<Tuple<Message, Connection>> buffer = sortByWeight(getMessagesForConnected());
		if (!buffer.isEmpty()){
			return buffer.get(0).getKey();
		}
		return null;
	}
	
	@Override
	public void update() {
		super.update();

//		if (!canStartTransfer() ||isTransferring()) {
//			return; // nothing to transfer or is currently transferring
//		}
	
		//sortBufferByWeight.
		//get first message han iya buffer, get first message han ak buffer
		//if the creationTime of otherNode's first message on the buffer is less than mine,
		//call exchangeUrgentMessages
		if (exchangeUrgentMessages() !=null){
			return;
		}
		
//		// try messages that could be delivered to final recipient
//		if (exchangeDeliverableMessages() != null) { ////bago ini, check anay kun hino it dapat mauna pag send between two connections
//			return;	
//		}
//		
//		tryOtherMessages();	
	} 
	
	public boolean shouldSendFirst(Connection c){
		DTNHost other = c.getOtherNode(getHost());
		Message m1 = getFirstMessageOnBuffer();
		Message m2 = ((TVProphetRouterV2) other.getRouter()).getFirstMessageOnBuffer();
		
		if (m2==null){ //otherNode has no message to send
			return true;
		}
//		System.out.println("CHECKING @ shouldsendfirst");
//		System.out.println("Message of " + getHost() + " : " + m1 + " timecreated: "+m1.getCreationTime());
//		System.out.println("Message of " + c.getOtherNode(getHost()) + " : " + m2 + " timecreated: "+m2.getCreationTime());
	
		
		int weight1 = (int) m1.getProperty(MESSAGE_WEIGHT);
		int weight2 = (int) m2.getProperty(MESSAGE_WEIGHT);
		if (weight1<=weight2){ //evaluate whose message is more urgent (with respect to time) m1.getCreationTime() <= m2.getCreationTime() || 
			return true;
		}
		return false;
	}
	
	/*
	 * Prioritizes sending messages that has lesser weight
	 */
	private Connection exchangeUrgentMessages(){
		List<Connection> connections = getConnections();

		if (connections.size() == 0) {
			return null;
		}
		Tuple<Message, Connection> t = null;
		List<Tuple<Message, Connection>> buffer = sortByWeight(getMessagesForConnected());
		if (!buffer.isEmpty()){
			for (Connection c : connections){
				if (shouldSendFirst(c)){
//					System.out.println("SHOULD SEND FIRST "  + getHost());
					t = tryMessagesForConnected(buffer);
					System.out.println("SPEED OF THIS TRANFER: " + c.getSpeed() );
				}
			}
		}
		if (t!=null){
			return t.getValue();
		}

		// didn't start transfer to any node -> ask messages from connected
		for (Connection con : connections) {
			if (con.getOtherNode(getHost()).requestDeliverableMessages(con)) {
				return con;
			}
		}
		return null;
	}
	
	/*
	 * Sorts messages depending on their weight
	 */
	@SuppressWarnings(value = "unchecked") /* ugly way to make this generic */
	private List sortByWeight(List list){
		Collections.sort(list,
				new Comparator() {
			/** Compares two tuples by their messages' weight */
			public int compare(Object o1, Object o2) {
				double diff;
				Message m1, m2;

				if (o1 instanceof Tuple) {
					m1 = ((Tuple<Message, Connection>)o1).getKey();
					m2 = ((Tuple<Message, Connection>)o2).getKey();
				}
				else if (o1 instanceof Message) {
					m1 = (Message)o1;
					m2 = (Message)o2;
				}
				else {
					throw new SimError("Invalid type of objects in " +
							"the list");
				}

				int weight1 = (int) m1.getProperty(MESSAGE_WEIGHT);
				int weight2 = (int) m2.getProperty(MESSAGE_WEIGHT);
				diff = weight1- weight2;
				if (diff == 0) {
					return 0;
				}
				return (diff < 0 ? -1 : 1);
			}
		});
		return list;
	}
	
	public Message getStoredMessage(String id) {
		for (Message m : getMessageCollection()){
			if(m.getId().equals(id)){
				return m;
			}
		}
		return null;
	}
	
	public void addUrgentMessage(Message m, boolean newMessage){ //butngi another parameter, pwd man didi nala magdelete mga diri kailangan
		addToMessages(m, newMessage);
	}
	
	public List<Tuple<Message, Connection>> getMessagesForConnected(){
		return super.getMessagesForConnected();		
	}
	
	@Override
	public MessageRouter replicate() {
		TVProphetRouterV2 r = new TVProphetRouterV2(this);
		return r;
	}
	
	/////prophet functions starts here -----------------
	
	/**
	 * Initializes historical contact durations
	 */
	private void initTava(){
		this.tava = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes historical transmission durations
	 */
	private void initVava(){
		this.vava = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes transmission predictions
	 */
	private void initTransmissionPreds(){
		this.transmissionPreds = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Initializes lastEncouterTime hash
	 */
	private void initEncTimes() {
		this.lastEncouterTime = new HashMap<DTNHost, Double>();
	}

		/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages =
			new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			
			DTNHost other = con.getOtherNode(getHost());
			TVProphetRouterV2 othRouter = (TVProphetRouterV2)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				//if prediction for this destination on other's router >= prediction for this destination on this host
				if((othRouter.getPredFor(m.getTo()) >= getPredFor(m.getTo()))) 
				{
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((TVProphetRouterV2)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((TVProphetRouterV2)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
			
				return 1;
			}
		}
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
			secondsInTimeUnit;	

		if (timeDiff == 0) {
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}

	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() +
				" delivery prediction(s)");
		RoutingInfo transSize = new RoutingInfo(transmissionPreds.size() + " transmission prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
					host, value)));
		}
		for (Entry<DTNHost, Double> t : transmissionPreds.entrySet()){
			DTNHost host = t.getKey();
			Double value = t.getValue();

			transSize.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", host, value)));
		}

		
		top.addMoreInfo(ri);
		top.addMoreInfo(transSize);
		return top;
	}
	
	
	
	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		int recvCheck = super.checkReceiving(m, from);

		if (recvCheck == RCV_OK) {
			/* don't accept a message that has already traversed this node */
			if (m.getHops().contains(getHost())) {
				recvCheck = DENIED_OLD;
			}
		}
		return recvCheck;
	}

}
