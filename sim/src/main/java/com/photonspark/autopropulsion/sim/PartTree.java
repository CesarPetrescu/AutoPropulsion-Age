package com.photonspark.autopropulsion.sim;
import java.util.*;

/** Transactional slot tree. Paths are relative; a removed parent cannot leave orphaned parts. */
public final class PartTree {
    public record Slot(String id, Set<String> accepts) {
        public Slot { if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Bad slot id"); accepts = Set.copyOf(accepts); }
    }
    public record Part(String id, Set<String> tags, Set<String> requires, Set<String> excludes, List<Slot> addsSlots) {
        public Part { Objects.requireNonNull(id); tags=Set.copyOf(tags); requires=Set.copyOf(requires); excludes=Set.copyOf(excludes); addsSlots=List.copyOf(addsSlots); }
    }
    private final Map<String,Slot> roots;
    private final Set<String> chassisTags;
    private final TreeMap<String,Part> installed = new TreeMap<>();
    public PartTree(List<Slot> slots, Set<String> chassisTags) {
        var map = new TreeMap<String,Slot>();
        for (Slot s:slots) if(map.put(s.id(),s)!=null) throw new IllegalArgumentException("Duplicate slot");
        this.roots=Map.copyOf(map); this.chassisTags=Set.copyOf(chassisTags);
    }
    public Map<String,Part> installed() { return Collections.unmodifiableMap(new TreeMap<>(installed)); }
    private Slot slot(String path) {
        if (path.length()>256 || path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("Bad slot path");
        int sep=path.lastIndexOf('/');
        if(sep<0) return roots.get(path);
        Part parent=installed.get(path.substring(0,sep));
        if(parent==null) return null;
        String leaf=path.substring(sep+1);
        return parent.addsSlots().stream().filter(s->s.id().equals(leaf)).findFirst().orElse(null);
    }
    public List<String> incompatibilities(String path, Part part) {
        var reasons = new ArrayList<String>();
        Slot s=slot(path);
        if(s==null) { reasons.add("No exposed slot: "+path); return List.copyOf(reasons); }
        if(Collections.disjoint(s.accepts(),part.tags())) reasons.add("Slot rejects the part's tags");
        var tags=new HashSet<>(chassisTags);
        installed.forEach((p,v)->{if(!p.equals(path)&&!p.startsWith(path+"/")) tags.addAll(v.tags());});
        if(!tags.containsAll(part.requires())) reasons.add("Required tags missing");
        if(!Collections.disjoint(tags,part.excludes())) reasons.add("Excluded combination");
        for(var e:installed.entrySet()) if(!e.getKey().equals(path)&&!e.getKey().startsWith(path+"/"))
            if(!Collections.disjoint(e.getValue().excludes(),part.tags())) reasons.add("Existing part excludes replacement");
        if(installed.keySet().stream().anyMatch(p->p.startsWith(path+"/"))) reasons.add("Remove children before replacing their parent");
        return List.copyOf(reasons);
    }
    public Optional<Part> install(String path, Part part) {
        List<String> reasons=incompatibilities(path,part);
        if(!reasons.isEmpty()) throw new IllegalArgumentException(String.join("; ",reasons));
        if(installed.size()>=512&&!installed.containsKey(path)) throw new IllegalArgumentException("Assembly capacity exceeded");
        return Optional.ofNullable(installed.put(path,part));
    }
    public Map<String,Part> removeSubtree(String path) {
        var removed=new TreeMap<String,Part>();
        installed.forEach((p,v)->{if(p.equals(path)||p.startsWith(path+"/")) removed.put(p,v);});
        var remainingTags=new HashSet<>(chassisTags);
        installed.forEach((p,v)->{if(!removed.containsKey(p)) remainingTags.addAll(v.tags());});
        for(var e:installed.entrySet()) if(!removed.containsKey(e.getKey())&&!remainingTags.containsAll(e.getValue().requires()))
            throw new IllegalArgumentException("Removal would break "+e.getKey());
        removed.keySet().forEach(installed::remove);
        return Collections.unmodifiableMap(removed);
    }
}
