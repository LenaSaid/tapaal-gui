package dk.aau.cs.translations.tapn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dk.aau.cs.TA.Edge;
import dk.aau.cs.TA.Location;
import dk.aau.cs.TA.NTA;
import dk.aau.cs.TA.StandardUPPAALQuery;
import dk.aau.cs.TA.TimedAutomaton;
import dk.aau.cs.TA.UPPAALQuery;
import dk.aau.cs.TCTL.visitors.BroadcastTranslationQueryVisitor;
import dk.aau.cs.petrinet.Arc;
import dk.aau.cs.petrinet.PetriNetUtil;
import dk.aau.cs.petrinet.TAPNArc;
import dk.aau.cs.petrinet.TAPNInhibitorArc;
import dk.aau.cs.petrinet.TAPNPlace;
import dk.aau.cs.petrinet.TAPNQuery;
import dk.aau.cs.petrinet.TAPNTransition;
import dk.aau.cs.petrinet.TAPNTransportArc;
import dk.aau.cs.petrinet.TimedArcPetriNet;
import dk.aau.cs.petrinet.Token;
import dk.aau.cs.translations.ModelTranslator;
import dk.aau.cs.translations.Pairing;
import dk.aau.cs.translations.QueryTranslator;
import dk.aau.cs.translations.TranslationNamingScheme;
import dk.aau.cs.translations.Pairing.ArcType;

public class BroadcastTranslation implements
ModelTranslator<TimedArcPetriNet, NTA>,
QueryTranslator<TAPNQuery, UPPAALQuery>{

	private int extraTokens;
	private int largestPresetSize = 0;
	private int initTransitions = 0;
	protected boolean useSymmetry = false;

	protected static final String ID_TYPE = "id_t";
	protected static final String ID_TYPE_NAME = "id";
	protected static final String TOKEN_INTERMEDIATE_PLACE = "%1$s_%2$s_%3$d";
	protected static final String TEST_CHANNEL_NAME = "%1$s_test%2$s";
	protected static final String FIRE_CHANNEL_NAME = "%1$s_fire%2$s";
	protected static final String COUNTER_NAME = "count%1$d";
	protected static final String COUNTER_UPDATE = "%1$s%2$s";
	protected static final String TOKEN_CLOCK_NAME = "x";
	protected static final String PLOCK = "P_lock";
	protected static final String PCAPACITY = "P_capacity";
	protected static final String INITIALIZE_CHANNEL = "c%1$d%2$s";

	protected static final String CONTROL_TEMPLATE_NAME = "Control";
	protected static final String TOKEN_TEMPLATE_NAME = "Token";
	protected static final String QUERY_PATTERN = "([a-zA-Z][a-zA-Z0-9_]*) (==|<|<=|>=|>) ([0-9])*";
	protected static final String LOCK_BOOL = "lock";

	private Hashtable<String, Location> namesToLocations = new Hashtable<String, Location>();
	protected Hashtable<Arc, String> arcsToCounters = new Hashtable<Arc, String>();
	public BroadcastTranslation(int extraTokens, boolean useSymmetry){
		this.extraTokens = extraTokens;
		this.useSymmetry = useSymmetry;
	}


	public NTA transformModel(TimedArcPetriNet model) throws Exception {
		clearLocationMappings();
		arcsToCounters.clear();
		largestPresetSize = 0;	
		initTransitions = model.getNumberOfTokens();

		try{
			model.convertToConservative();
		}catch(Exception e){
			return null;
		}

		NTA nta = new NTA();

		if(useSymmetry){
			TimedAutomaton tokenTemplate = createTokenTemplate(model, null);
			addInitializationStructure(tokenTemplate, model);
			tokenTemplate.setName(TOKEN_TEMPLATE_NAME);
			tokenTemplate.setParameters("const " + ID_TYPE + " " + ID_TYPE_NAME);
			tokenTemplate.setInitLocation(getLocationByName(PCAPACITY));
			nta.addTimedAutomaton(tokenTemplate);
		}else{
			int j = 0;
			for(Token token : model.getTokens()){
				clearLocationMappings();
				arcsToCounters.clear();
				TimedAutomaton tokenTemplate = createTokenTemplate(model, token);
				tokenTemplate.setInitLocation(getLocationByName(token.place().getName()));
				nta.addTimedAutomaton(tokenTemplate);
				tokenTemplate.setName(TOKEN_TEMPLATE_NAME + j);
				j++;
			}

			for(int i = 0; i < extraTokens; i++){
				clearLocationMappings();
				arcsToCounters.clear();
				TimedAutomaton tokenTemplate = createTokenTemplate(model, new Token(model.getPlaceByName(PCAPACITY)));
				tokenTemplate.setInitLocation(getLocationByName(PCAPACITY));
				nta.addTimedAutomaton(tokenTemplate);
				tokenTemplate.setName(TOKEN_TEMPLATE_NAME + String.valueOf(model.getNumberOfTokens()+i));
			}
		}

		TimedAutomaton controlTemplate = createControlTemplate(model);
		nta.addTimedAutomaton(controlTemplate);

		nta.setSystemDeclarations(createSystemDeclaration(model.getNumberOfTokens()));

		String globalDecl = createGlobalDeclarations(model);
		nta.setGlobalDeclarations(globalDecl);

		return nta;
	}


	private String createSystemDeclaration(int tokensInModel) {
		if(useSymmetry){
			return "system " + CONTROL_TEMPLATE_NAME + "," + TOKEN_TEMPLATE_NAME + ";";
		}else{
			StringBuilder builder = new StringBuilder("system ");
			builder.append(CONTROL_TEMPLATE_NAME);

			for(int i = 0; i < extraTokens + tokensInModel; i++){
				builder.append(", ");
				builder.append(TOKEN_TEMPLATE_NAME);
				builder.append(i);
			}
			builder.append(";");

			return builder.toString();
		}
	}



	private String createGlobalDeclarations(TimedArcPetriNet model) {
		StringBuilder builder = new StringBuilder();
		builder.append("const int N = ");
		builder.append(model.getTokens().size() + extraTokens);
		builder.append(";\n");

		if(useSymmetry){
			builder.append("typedef ");
			builder.append("scalar[N] ");
			builder.append(ID_TYPE);
			builder.append(";\n");

			for(int i = 0; i < initTransitions; i++){
				builder.append("chan c");
				builder.append(i);
				builder.append(";\n");
			}
		}		

		for(TAPNTransition t : model.getTransitions()){
			if(t.isDegree1() && !t.hasInhibitorArcs()){
				builder.append("broadcast chan ");
				builder.append(t.getName());
				builder.append(";");
			}else if(t.isDegree2() && !t.hasInhibitorArcs()){
				builder.append("chan ");
				builder.append(t.getName());
				builder.append(";\n");
			}else{			
				builder.append("broadcast chan ");
				builder.append(String.format(TEST_CHANNEL_NAME, t.getName(),""));
				builder.append(",");
				builder.append(String.format(FIRE_CHANNEL_NAME, t.getName(),""));
				builder.append(";\n");
			}
		}

		for(int i = 0; i < largestPresetSize; i++){
			builder.append("int[0,N] ");
			builder.append(String.format(COUNTER_NAME, i));
			builder.append(";\n");
		}

		builder.append("bool ");
		builder.append(LOCK_BOOL);
		builder.append(" = false;\n");

		return builder.toString();
	}

	private TimedAutomaton createControlTemplate(TimedArcPetriNet model) {
		TimedAutomaton control = new TimedAutomaton();
		control.setName(CONTROL_TEMPLATE_NAME);

		Location lock = new Location(PLOCK, ""); 
		control.addLocation(lock);

		if(useSymmetry){
			Location last = createInitializationStructure(control, initTransitions);

			if(last == null){
				control.setInitLocation(lock);
			}else{
				Edge e = new Edge(last,
						lock,
						"",
						String.format(INITIALIZE_CHANNEL, initTransitions-1, "!"),
				"");
				control.addTransition(e);
			}
		}else{
			control.setInitLocation(lock);
		}

		createTransitionSimulations(control, lock, model);

		return control;
	}

	protected void createTransitionSimulations(TimedAutomaton control, Location lock,
			TimedArcPetriNet model) {

		for(TAPNTransition transition : model.getTransitions()){
			if(!transition.isDegree2() || transition.hasInhibitorArcs()){
				String invariant = createInvariantForControl(transition);

				Location tempLoc = new Location("",invariant);
				tempLoc.setCommitted(true);
				control.addLocation(tempLoc);

				Edge testEdge = new Edge(lock,
						tempLoc,
						"",
						String.format(TEST_CHANNEL_NAME, transition.getName(), "!"),
						lockUpdateStatement(true));
				control.addTransition(testEdge);

				Edge fireEdge = new Edge(tempLoc,
						lock,
						createGuardForControl(transition),
						String.format(FIRE_CHANNEL_NAME, transition.getName(), "!"),
						createResetExpressionForControl(transition));
				control.addTransition(fireEdge);
			}	
		}
	}

	private String lockUpdateStatement(boolean value) {
		return LOCK_BOOL + " = " + value;
	}


	protected String createResetExpressionForControl(TAPNTransition transition) {
		StringBuilder builder = new StringBuilder();

		boolean first = true;

		for(Arc presetArc : transition.getPreset()){
			if(!first){
				builder.append(", ");
			}

			String counter = arcsToCounters.get(presetArc);
			builder.append(counter);
			builder.append(":=0");
			first = false;
		}

		for(TAPNInhibitorArc inhib : transition.getInhibitorArcs()){
			if(!first){
				builder.append(", ");
			}

			String counter = arcsToCounters.get(inhib);
			builder.append(counter);
			builder.append(":=0");
			first = false;
		}

		if(!first){
			builder.append(", ");
		}
		builder.append(lockUpdateStatement(false));

		return builder.toString();
	}

	private String createGuardForControl(TAPNTransition transition) {
		return createBooleanExpressionForControl(transition, "==", "==", 1);
	}

	protected String createInvariantForControl(TAPNTransition transition) {
		return createBooleanExpressionForControl(transition, ">=", "==",1);
	}

	protected String createBooleanExpressionForControl(TAPNTransition transition, String comparison, String inhibComparison, int number)
	{
		StringBuilder builder = new StringBuilder();

		boolean first = true;

		for(Arc presetArc : transition.getPreset()){
			if(!first){
				builder.append(" && ");
			}

			String counter = arcsToCounters.get(presetArc);
			builder.append(counter);
			builder.append(comparison);
			builder.append(number);
			first = false;
		}

		for(TAPNInhibitorArc inhib : transition.getInhibitorArcs()){
			if(!first){
				builder.append(" && ");
			}

			String counter = arcsToCounters.get(inhib);
			builder.append(counter);
			builder.append(inhibComparison);
			builder.append("0");
		}

		return builder.toString();
	}

	private Location createInitializationStructure(TimedAutomaton control,
			int numberOfTokens) {

		Location previous = null;

		for(int i = 0; i <= numberOfTokens-1; i++){
			Location loc = new Location("","");
			loc.setCommitted(true);
			control.addLocation(loc);

			if(previous != null){
				Edge e = new Edge(previous, 
						loc, 
						"", 
						String.format(INITIALIZE_CHANNEL, i-1, "!"),
				"");
				control.addTransition(e);
			}else{
				control.setInitLocation(loc);
			}

			previous = loc;
		}

		return previous;
	}

	private TimedAutomaton createTokenTemplate(TimedArcPetriNet model, Token token) {		
		TimedAutomaton ta = new TimedAutomaton();

		String declarations = createLocalDeclarations(model, token);
		ta.setDeclarations(declarations);
		createTemplateStructure(ta, model);

		return ta;
	}


	protected String createLocalDeclarations(TimedArcPetriNet model, Token token) {
		return "clock " + TOKEN_CLOCK_NAME + ";";
	}

	protected void addInitializationStructure(TimedAutomaton ta,
			TimedArcPetriNet model) {
		int i = 0;

		for(Token token : model.getTokens()){
			Edge initEdge = new Edge(getLocationByName(PCAPACITY),
					getLocationByName(token.place().getName()),
					"",
					String.format(INITIALIZE_CHANNEL, i, "?"),
					createUpdateExpressionForTokenInitialization(token));
			ta.addTransition(initEdge);
			i++;
		}		
	}

	protected String createUpdateExpressionForTokenInitialization(Token token) {
		return "";
	}


	private void createTemplateStructure(TimedAutomaton ta,
			TimedArcPetriNet model) {
		ta.setLocations(CreateLocationsFromModel(model));

		for(TAPNTransition t : model.getTransitions()){
			int presetSize = t.getPreset().size() + t.getInhibitorArcs().size();
			if(presetSize > largestPresetSize){
				largestPresetSize = presetSize;
			}

			if(t.isDegree2() && !t.hasInhibitorArcs()){
				createDegree2Structure(ta,t);
			}else{
				List<Pairing> pairing = CreatePairing(t);
				createStructureForPairing(ta, t, pairing);
			}
		}	
	}


	private void createDegree2Structure(TimedAutomaton ta, TAPNTransition t) {
		List<Pairing> pairing = CreatePairing(t);

		if(pairing.size() == 0) return;
		if(pairing.size() == 1){
			Pairing pair = pairing.get(0);

			Edge e = new Edge(getLocationByName(pair.getInput().getName()),
					getLocationByName(pair.getOutput().getName()),
					createTransitionGuardWithLock(pair.getInputArc(), pair.getOutputArc(), pair.getOutput(), pair.getArcType()==ArcType.TARC),
					t.getName() + "!",
					createResetExpressionIfNormalArc(pair.getOutputArc()));

			ta.addTransition(e);
		}else{
			Pairing pair1 = pairing.get(0);

			Edge e1 = new Edge(getLocationByName(pair1.getInput().getName()),
					getLocationByName(pair1.getOutput().getName()),
					createTransitionGuardWithLock(pair1.getInputArc(), pair1.getOutputArc(), pair1.getOutput(), pair1.getArcType()==ArcType.TARC),
					t.getName() + "?",
					createResetExpressionIfNormalArc(pair1.getOutputArc()));

			ta.addTransition(e1);

			Pairing pair2 = pairing.get(1);

			Edge e2 = new Edge(getLocationByName(pair2.getInput().getName()),
					getLocationByName(pair2.getOutput().getName()),
					createTransitionGuardWithLock(pair2.getInputArc(), pair2.getOutputArc(), pair2.getOutput(), pair2.getArcType()==ArcType.TARC),
					t.getName() + "!",
					createResetExpressionIfNormalArc(pair2.getOutputArc()));

			ta.addTransition(e2);
		}
	}


	private String createTransitionGuardWithLock(TAPNArc inputArc, Arc outputArc,
			TAPNPlace output, boolean isTarc) {
		String guard = createTransitionGuard(inputArc, outputArc, output, isTarc);

		if(guard == null || guard.isEmpty()){
			guard = LOCK_BOOL + " == 0";
		}else{
			guard += " && " + LOCK_BOOL + " == 0";
		}

		return guard;
	}


	protected void createStructureForPairing(TimedAutomaton ta, TAPNTransition t,
			List<Pairing> pairing) {
		int i = 0;
		for(Pairing pair : pairing){
			String inputPlaceName = pair.getInput().getName();
			String locationName = String.format(TOKEN_INTERMEDIATE_PLACE, inputPlaceName, t.getName(), i);

			Location intermediate = new Location(locationName, "");
			intermediate.setCommitted(true);
			ta.addLocation(intermediate);
			addLocationMapping(locationName, intermediate);

			String counter = String.format(COUNTER_NAME, i);
			arcsToCounters.put(pair.getInputArc(), counter);

			Edge testEdge = new Edge(getLocationByName(inputPlaceName), 
					intermediate, 
					createTransitionGuard(pair.getInputArc(), pair.getOutputArc(), pair.getOutput(), pair.getArcType()==ArcType.TARC),
					String.format(TEST_CHANNEL_NAME, t.getName(), "?"),
					String.format(COUNTER_UPDATE, counter, "++"));
			ta.addTransition(testEdge);

			Edge fireEdge = new Edge(intermediate,
					getLocationByName(pair.getOutput().getName()),
					"", //String.format(COUNTER_UPDATE, i, "==1"),
					String.format(FIRE_CHANNEL_NAME, t.getName(), "?"),
					createResetExpressionIfNormalArc(pair.getOutputArc()));
			ta.addTransition(fireEdge);

			String guard = String.format(COUNTER_UPDATE, counter,">1");

			Edge backEdge = new Edge(intermediate,
					getLocationByName(inputPlaceName),
					guard,
					"",
					String.format(COUNTER_UPDATE, counter, "--"));
			ta.addTransition(backEdge);

			i++;
		}

		createStructureForInhibitorArcs(ta, t, i);
	}


	protected void createStructureForInhibitorArcs(TimedAutomaton ta,
			TAPNTransition t, int i) {
		for(TAPNInhibitorArc inhibArc : t.getInhibitorArcs()){
			String inputPlace = inhibArc.getSource().getName();

			String counter = String.format(COUNTER_NAME, i);
			arcsToCounters.put(inhibArc, counter);

			Location location = getLocationByName(inputPlace);
			Edge inhibEdge = new Edge(location,
					location,
					createTransitionGuard(inhibArc,null,null, false),
					String.format(TEST_CHANNEL_NAME, t.getName(),"?"),
					String.format(COUNTER_UPDATE, counter, "++"));
			ta.addTransition(inhibEdge);
			i++;
		}
	}

	protected String createResetExpressionIfNormalArc(Arc arc) {
		if(!(arc instanceof TAPNTransportArc)){
			return String.format("%1s := 0", TOKEN_CLOCK_NAME);
		}else{
			return "";
		}
	}

	private List<Pairing> CreatePairing(TAPNTransition t) {
		List<Pairing> pairing = new ArrayList<Pairing>();
		HashSet<Arc> usedPostSetArcs = new HashSet<Arc>();

		for(Arc inputArc : t.getPreset()){
			for(Arc outputArc : t.getPostset()){
				if(!usedPostSetArcs.contains(outputArc)){
					if(inputArc instanceof TAPNTransportArc && outputArc instanceof TAPNTransportArc && inputArc == outputArc){
						Pairing p = new Pairing((TAPNArc)inputArc,
								((TAPNArc)inputArc).getGuard(),
								outputArc,
								ArcType.TARC);
						pairing.add(p);

						usedPostSetArcs.add(outputArc);
						break;
					}else if(!(inputArc instanceof TAPNTransportArc) && !(outputArc instanceof TAPNTransportArc)){
						Pairing p = new Pairing((TAPNArc)inputArc,
								((TAPNArc)inputArc).getGuard(),
								outputArc,
								ArcType.NORMAL);
						pairing.add(p);

						usedPostSetArcs.add(outputArc);
						break;
					}
				}
			}
		}

		return pairing;
	}

	private ArrayList<Location> CreateLocationsFromModel(TimedArcPetriNet model) {
		clearLocationMappings();

		ArrayList<Location> locations = new ArrayList<Location>();
		for(TAPNPlace p : model.getPlaces()){
			Location l = new Location(p.getName(), convertInvariant(p));

			locations.add(l);	
			addLocationMapping(p.getName(), l);
		}

		return locations;
	}

	protected String createTransitionGuard(TAPNArc inputArc, Arc outputArc, TAPNPlace target, boolean isTransportArc) {
		String newGuard = PetriNetUtil.createGuard(inputArc.getGuard(), target, isTransportArc);
		return createTransitionGuard(newGuard);
	}

	protected String createTransitionGuard(String guard) {
		if(guard.equals("false")) return guard;
		if(guard.equals("[0,inf)")) return "";

		String[] splitGuard = guard.substring(1, guard.length()-1).split(",");
		char firstDelim = guard.charAt(0);
		char secondDelim = guard.charAt(guard.length()-1);

		StringBuilder builder = new StringBuilder();
		builder.append(TOKEN_CLOCK_NAME);
		builder.append(" ");

		if(firstDelim == '('){
			builder.append(">");
		} else {
			builder.append(">=");
		}

		builder.append(splitGuard[0]);

		if(!splitGuard[1].equals("inf")){
			builder.append(" && ");
			builder.append(TOKEN_CLOCK_NAME);
			builder.append(" ");

			if(secondDelim == ')'){
				builder.append("<");
			}else {
				builder.append("<=");
			}
			builder.append(splitGuard[1]);
		}

		return builder.toString();
	}

	protected String convertInvariant(TAPNPlace place) {
		String inv = "";
		String invariant = place.getInvariant();
		if(!invariant.equals("<inf")){
			inv = TOKEN_CLOCK_NAME + " " + invariant;
		}

		return inv;
	}

	protected Location getLocationByName(String name){
		return namesToLocations.get(name);
	}

	protected void addLocationMapping(String name, Location location){
		namesToLocations.put(name, location);
	}

	protected void clearLocationMappings(){
		namesToLocations.clear();
	}


	public UPPAALQuery transformQuery(TAPNQuery tapnQuery) throws Exception {
		BroadcastTranslationQueryVisitor visitor = new BroadcastTranslationQueryVisitor(useSymmetry, tapnQuery.getTotalTokens());
		
		return new StandardUPPAALQuery(visitor.getUppaalQueryFor(tapnQuery));
	}
	
	
	public TranslationNamingScheme namingScheme(){
		return new BroadcastNamingScheme();
	}
	
	protected class BroadcastNamingScheme implements TranslationNamingScheme {
		private final String TAU = "tau";
		private final String START_OF_SEQUENCE_PATTERN = "^(\\w+?)(?:_test)?$";
		private final String END_OF_SEQUENCE_PATTERN = "^(\\w+?)_fire$";
		private Pattern startPattern = Pattern.compile(START_OF_SEQUENCE_PATTERN);
		private Pattern endPattern = Pattern.compile(END_OF_SEQUENCE_PATTERN);
		
		public TransitionTranslation[] interpretTransitionSequence(List<String> firingSequence) {
			List<TransitionTranslation> transitionTranslations = new ArrayList<TransitionTranslation>();
			
			for(int i = 0; i < firingSequence.size(); i++){
				String transitionName = firingSequence.get(i);
				if(!isIgnoredTransition(transitionName)){
					Matcher startMatcher = startPattern.matcher(transitionName);
					Matcher endMatcher = endPattern.matcher(transitionName);
					
					if(startMatcher.find() && !endMatcher.find() && !transitionName.equals(TAU)){
						transitionTranslations.add(new TransitionTranslation(i, startMatcher.group(1)));
					}			
				}
			}
			
			TransitionTranslation[] array = new TransitionTranslation[transitionTranslations.size()];
			transitionTranslations.toArray(array);
			return array;
		}

		private boolean isIgnoredTransition(String string) {
			Pattern pattern = Pattern.compile("c\\d+");
			Matcher matcher = pattern.matcher(string);
			return matcher.find();
		}

		public String tokenClockName() {
			return TOKEN_CLOCK_NAME;
		}
		
		public boolean isIgnoredPlace(String location) {
			return location.equals(PLOCK) ||  location.equals(PCAPACITY);
		}
		
		public boolean isIgnoredAutomata(String automata){
			return automata.equals(CONTROL_TEMPLATE_NAME);
		}
	}
}