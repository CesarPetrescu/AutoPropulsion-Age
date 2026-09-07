package com.photonspark.sparkmotors.sim;

/** Exports the exact compiled item catalog for recipes, icons and documentation. */
public final class PartCatalog {
    private static String q(String text){return "\""+text.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    public static void main(String[] args){
        System.out.print("{\"schema\":3,\"slots\":[");boolean firstSlot=true;
        for(var slot:EnginePart.values()){
            if(!firstSlot)System.out.print(",");firstSlot=false;
            System.out.print("{\"index\":"+slot.ordinal()+",\"id\":"+q(slot.id)+",\"title\":"+q(slot.title)+",\"options\":[");
            for(int v=1;v<=slot.maxVariant();v++){
                if(v>1)System.out.print(",");
                System.out.print("{\"value\":"+v+",\"item\":"+q(slot.itemName(v))+",\"label\":"+q(slot.label(v))+",\"description\":"+q(slot.description(v))+"}");
            }System.out.print("]}");
        }System.out.println("]}");
    }
}
