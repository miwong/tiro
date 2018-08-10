package tiro.target.event;

import tiro.*;

import soot.jimple.Stmt;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EventChain {
    static AtomicInteger _nextId = new AtomicInteger();

    private final int _id;

    // Events stored in reverse-dependence order
    private List<Event> _events = new ArrayList<Event>();
    private String _startMethod = null;

    public EventChain() {
        _id = _nextId.getAndIncrement();
    }

    public int getId() {
        return _id;
    }

    public List<Event> getEvents() {
        return Lists.reverse(_events);
    }

    public void addDependentEvent(Event event) {
        _events.add(event);
        _startMethod = event.getPath().getEntryMethod().getSignature();
    }

    public JsonObject toJson() {
        String eventChainDirectory = TIROStaticAnalysis.Config.OutputDirectory
                + "/constraints/" + _id;
        (new File(eventChainDirectory)).mkdirs();

        JsonObject eventChainJson = new JsonObject();

        eventChainJson.addProperty("Start", this.getStart());
        eventChainJson.addProperty("Target", this.getTarget());

        JsonArray eventsJson = new JsonArray();
        List<Event> events = Lists.reverse(_events);
        for (int eventId = 0; eventId < events.size(); eventId++) {
            Event event = events.get(eventId);
            eventsJson.add(event.toJson(eventChainDirectory, eventId));
        }
        eventChainJson.add("Events", eventsJson);

        return eventChainJson;
    }

    private String getStart() {
        return _startMethod;
    }

    private String getTarget() {
        // Target of an event chain should always be an invoke statement
        Stmt targetStmt = (Stmt)_events.get(0).getPath().getTargetUnit();
        return targetStmt.getInvokeExpr().getMethodRef().getSignature();
    }
}
