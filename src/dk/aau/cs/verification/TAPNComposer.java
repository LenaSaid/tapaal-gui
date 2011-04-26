package dk.aau.cs.verification;

import java.util.HashSet;

import dk.aau.cs.model.tapn.SharedPlace;
import dk.aau.cs.model.tapn.TimedArcPetriNet;
import dk.aau.cs.model.tapn.TimedArcPetriNetNetwork;
import dk.aau.cs.model.tapn.TimedInhibitorArc;
import dk.aau.cs.model.tapn.TimedInputArc;
import dk.aau.cs.model.tapn.TimedOutputArc;
import dk.aau.cs.model.tapn.LocalTimedPlace;
import dk.aau.cs.model.tapn.TimedPlace;
import dk.aau.cs.model.tapn.TimedToken;
import dk.aau.cs.model.tapn.TimedTransition;
import dk.aau.cs.model.tapn.TransportArc;
import dk.aau.cs.util.Tuple;

public class TAPNComposer {
	private static final String PLACE_FORMAT = "P%1$d";
	private static final String TRANSITION_FORMAT = "T%1$d";

	private int nextPlaceIndex;
	private int nextTransitionIndex;

	private HashSet<String> processedSharedObjects;

	public Tuple<TimedArcPetriNet, NameMapping> transformModel(TimedArcPetriNetNetwork model) {
		nextPlaceIndex = -1;
		nextTransitionIndex = -1;
		processedSharedObjects = new HashSet<String>();
		TimedArcPetriNet tapn = new TimedArcPetriNet("ComposedModel");
		NameMapping mapping = new NameMapping();

		createSharedPlaces(model, tapn, mapping);
		createPlaces(model, tapn, mapping);
		createTransitions(model, tapn, mapping);
		createInputArcs(model, tapn, mapping);
		createOutputArcs(model, tapn, mapping);
		createTransportArcs(model, tapn, mapping);
		createInhibitorArcs(model, tapn, mapping);

		//dumpToConsole(tapn, mapping);

		return new Tuple<TimedArcPetriNet, NameMapping>(tapn, mapping);
	}

	@SuppressWarnings("unused")
	private void dumpToConsole(TimedArcPetriNet tapn, NameMapping mapping) {
		System.out.println("Composed Model:");
		System.out.println("PLACES:");
		for(TimedPlace place : tapn.places()){
			System.out.print("\t");
			System.out.print(place.name());
			System.out.print(", invariant: ");
			System.out.print(place.invariant().toString());
			System.out.print(" (Original: ");
			System.out.print(mapping.map(place.name()));
			System.out.println(")");
		}

		System.out.println();
		System.out.println("TRANSITIONS:");
		for(TimedTransition transition : tapn.transitions()){
			System.out.print("\t");
			System.out.print(transition.name());
			System.out.print(" (Original: ");
			System.out.print(mapping.map(transition.name()).toString());
			System.out.println(")");
		}

		System.out.println();
		System.out.println("INPUT ARCS:");
		for(TimedInputArc arc : tapn.inputArcs()){
			System.out.print("\tSource: ");
			System.out.print(arc.source().name());
			System.out.print(", Target: ");
			System.out.print(arc.destination().name());
			System.out.print(", Interval: ");
			System.out.println(arc.interval().toString());
		}

		System.out.println();
		System.out.println("OUTPUT ARCS:");
		for(TimedOutputArc arc : tapn.outputArcs()){
			System.out.print("\tSource: ");
			System.out.print(arc.source().name());
			System.out.print(", Target: ");
			System.out.println(arc.destination().name());
		}

		System.out.println();
		System.out.println("TRANSPORT ARCS:");
		for(TransportArc arc : tapn.transportArcs()){
			System.out.print("\tSource: ");
			System.out.print(arc.source().name());
			System.out.print(", Transition: ");
			System.out.print(arc.transition().name());
			System.out.print(", Target: ");
			System.out.print(arc.destination().name());
			System.out.print(", Interval: ");
			System.out.println(arc.interval().toString());
		}

		System.out.println();
		System.out.println("INHIBITOR ARCS:");
		for(TimedInhibitorArc arc : tapn.inhibitorArcs()){
			System.out.print("\tSource: ");
			System.out.print(arc.source().name());
			System.out.print(", Target: ");
			System.out.print(arc.destination().name());
			System.out.print(", Interval: ");
			System.out.println(arc.interval().toString());
		}

		System.out.println();
		System.out.println("MARKING:");
		for(TimedPlace place : tapn.places()){
			for(TimedToken token : place.tokens()){
				System.out.print(token.toString());
				System.out.print(", ");
			}
		}
		System.out.println();
		System.out.println();
	}
	
	private void createSharedPlaces(TimedArcPetriNetNetwork model, TimedArcPetriNet constructedModel, NameMapping mapping) {
		for(SharedPlace place : model.sharedPlaces()){
			String uniquePlaceName = getUniquePlaceName();
			
			LocalTimedPlace constructedPlace = new LocalTimedPlace(uniquePlaceName, place.invariant());
			constructedModel.add(constructedPlace);
			mapping.addMappingForShared(place.name(), uniquePlaceName);

			if(isSharedPlaceUsedInTemplates(model, place)){
				for (TimedToken token : place.tokens()) {
					constructedPlace.addToken(new TimedToken(constructedPlace, token.age()));
				}
			}
		}
	}

	private boolean isSharedPlaceUsedInTemplates(TimedArcPetriNetNetwork model, SharedPlace place) {
		for(TimedArcPetriNet tapn : model.templates()){
			for(TimedPlace timedPlace : tapn.places()){
				if(timedPlace.equals(place)) return true;
			}
		}
		return false;
	}

	private void createPlaces(TimedArcPetriNetNetwork model, TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TimedPlace timedPlace : tapn.places()) {
				if(!timedPlace.isShared()){
					String uniquePlaceName = getUniquePlaceName();

					LocalTimedPlace place = new LocalTimedPlace(uniquePlaceName, timedPlace.invariant());
					constructedModel.add(place);
					mapping.addMapping(tapn.name(), timedPlace.name(), uniquePlaceName);

					for (TimedToken token : timedPlace.tokens()) {
						place.addToken(new TimedToken(place, token.age()));
					}
				}
			}
		}
	}

	private String getUniquePlaceName() {
		nextPlaceIndex++;
		return String.format(PLACE_FORMAT, nextPlaceIndex);
	}

	private void createTransitions(TimedArcPetriNetNetwork model, TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TimedTransition timedTransition : tapn.transitions()) {
				if(!processedSharedObjects.contains(timedTransition.name())){
					String uniqueTransitionName = getUniqueTransitionName();

					constructedModel.add(new TimedTransition(uniqueTransitionName));
					if(timedTransition.isShared()){
						String name = timedTransition.sharedTransition().name();
						processedSharedObjects.add(name);
						mapping.addMappingForShared(name, uniqueTransitionName);
					}else{
						mapping.addMapping(tapn.name(), timedTransition.name(), uniqueTransitionName);
					}
				}
			}
		}
	}

	private String getUniqueTransitionName() {
		nextTransitionIndex++;
		return String.format(TRANSITION_FORMAT, nextTransitionIndex);
	}

	private void createInputArcs(TimedArcPetriNetNetwork model,
			TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TimedInputArc arc : tapn.inputArcs()) {
				String template = arc.source().isShared() ? "" : tapn.name();
				TimedPlace source = constructedModel.getPlaceByName(mapping.map(template, arc.source().name()));
				
				template = arc.destination().isShared() ? "" : tapn.name();
				TimedTransition target = constructedModel.getTransitionByName(mapping.map(template, arc.destination().name()));

				constructedModel.add(new TimedInputArc(source, target, arc.interval()));
			}
		}
	}

	private void createOutputArcs(TimedArcPetriNetNetwork model,
			TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TimedOutputArc arc : tapn.outputArcs()) {
				String template = arc.source().isShared() ? "" : tapn.name();
				TimedTransition source = constructedModel.getTransitionByName(mapping.map(template, arc.source().name()));

				template = arc.destination().isShared() ? "" : tapn.name();
				TimedPlace target = constructedModel.getPlaceByName(mapping.map(template, arc.destination().name()));

				constructedModel.add(new TimedOutputArc(source, target));
			}
		}
	}

	private void createTransportArcs(TimedArcPetriNetNetwork model,
			TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TransportArc arc : tapn.transportArcs()) {
				String template = arc.source().isShared() ? "" : tapn.name();
				TimedPlace source = constructedModel.getPlaceByName(mapping.map(template, arc.source().name()));
				
				template = arc.transition().isShared() ? "" : tapn.name();
				TimedTransition transition = constructedModel.getTransitionByName(mapping.map(template, arc.transition().name()));
				
				template = arc.destination().isShared() ? "" : tapn.name();
				TimedPlace target = constructedModel.getPlaceByName(mapping.map(template, arc.destination().name()));

				constructedModel.add(new TransportArc(source, transition,target, arc.interval()));
			}
		}
	}

	private void createInhibitorArcs(TimedArcPetriNetNetwork model,
			TimedArcPetriNet constructedModel, NameMapping mapping) {
		for (TimedArcPetriNet tapn : model.templates()) {
			for (TimedInhibitorArc arc : tapn.inhibitorArcs()) {
				String template = arc.source().isShared() ? "" : tapn.name();
				TimedPlace source = constructedModel.getPlaceByName(mapping.map(template, arc.source().name()));

				template = arc.destination().isShared() ? "" : tapn.name();
				TimedTransition target = constructedModel.getTransitionByName(mapping.map(template, arc.destination().name()));

				constructedModel.add(new TimedInhibitorArc(source, target, arc.interval()));
			}
		}
	}
}