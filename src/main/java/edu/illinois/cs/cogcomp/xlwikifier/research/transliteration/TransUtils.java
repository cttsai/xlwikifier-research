package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by ctsai12 on 9/28/16.
 */
public class TransUtils {

    public static String del = "\\s+";
	public static boolean all_length = false;
    private static final int l_th = 15;

    /**
     * Read pre-processed pairs
     */
    public static List<Pair<String[], String[]>> readPairs(String infile){
        List<Pair<String[], String[]>> pairs = new ArrayList<>();
        try {
            for(String line: LineIO.read(infile)){
                String[] parts = line.split("\t");

                String[] parts1 = parts[0].split(del);
                String[] parts2 = parts[1].split("\\s+");


                pairs.add(new Pair<>(parts1, parts2));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return pairs;
    }

    public static List<Pair<String[], String[]>> readPairs(String infile, int max, int np_th){
        List<Pair<String, String>> titlepairs = new ArrayList<>();

        List<Pair<String[], String[]>> pairs = new ArrayList<>();

        double src_nt = 0, tgt_nt = 0;
        try {
            for(String line: LineIO.read(infile)){
                String[] parts = line.split("\t");
                if(parts.length < 2) continue;

//                if(parts[0].equals(parts[1])) continue;


                String[] parts1 = parts[0].split(del);
                String[] parts2 = parts[1].split("\\s+");
                src_nt += parts1.length;
                tgt_nt += parts2.length;

                if(parts1.length > np_th || parts2.length > np_th) continue;

                boolean bad = false;
                for(String part: parts1)
                    if(part.length()> l_th || part.trim().isEmpty()) bad = true;
                for(String part: parts2)
                    if(part.length()> l_th || part.trim().isEmpty()) bad = true;

                if(bad) continue;

                pairs.add(new Pair<>(parts1, parts2));
                titlepairs.add(new Pair<>(parts[0], parts[1]));

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Read "+pairs.size()+" pairs");
        System.out.println(src_nt/pairs.size()+" "+tgt_nt/pairs.size());

        if(max > 0) {
            titlepairs = titlepairs.subList(0, Math.min(max, titlepairs.size()));
            pairs = pairs.subList(0, Math.min(max, pairs.size()));
        }
        return pairs;
    }
    public static List<Pair<String, String>> alignWords(List<Pair<String[], String[]>> pairs){

        List<Pair<String, String>> ret = new ArrayList<>();

        for(Pair<String[] ,String[]> pair: pairs){


            if(pair.getFirst().length!=pair.getSecond().length) continue;

            for(int i = 0; i < pair.getFirst().length; i++) {
                ret.add(new Pair<>(pair.getFirst()[i], pair.getSecond()[i]));
//                ret.add(new Pair<>(pair.getSecond()[i], pair.getFirst()[i]));
            }
        }

        System.out.println("After aligning words, #pairs "+ret.size());
        return ret;
    }
    public static List<List<Integer>> getAllAssignments(int l, int m){
        List<List<Integer>> ret = new ArrayList<>();

        if(l == 0){
            ret.add(new ArrayList<>());
            return ret;
        }


        List<List<Integer>> ass = getAllAssignments(l - 1, m);
        for(int j = 0; j < m; j++){
            for(List<Integer> as: ass){
                List<Integer> tmp = new ArrayList<>(as);
                tmp.add(j);
                ret.add(tmp);
            }
        }

        return ret;
    }

    public static void updateExistMap(String key1, String key2, Double value, Map<String, Map<String, Double>> map){
        if(!map.containsKey(key1)) return;
        if(!map.get(key1).containsKey(key2)) return;

        map.get(key1).put(key1, value);

    }

    public static void addToMap(String key1, String key2, Double value, Map<String, Map<String, Double>> map){
        if(!map.containsKey(key1)) map.put(key1, new HashMap<>());

        Map<String, Double> submap = map.get(key1);

        if(!submap.containsKey(key2)) submap.put(key2, 0.0);

        submap.put(key2, submap.get(key2)+value);

    }

    public static void addToMap(String key, Double value, Map<String, Double> map){

        if(!map.containsKey(key)) map.put(key, 0.0);

        map.put(key, map.get(key)+value);

    }

    public static void normalizeProb(Map<String, Map<String, Double>> map){
//        System.out.println("Normalizing probs...");
        for(String s:  map.keySet()){
            Map<String, Double> t2prob = map.get(s);
            double sum = 0;
            for(String t: t2prob.keySet()){
                sum+=t2prob.get(t);
            }
            for(String t: t2prob.keySet()){
                t2prob.put(t, t2prob.get(t)/sum);
            }
        }
    }

    public static void normalizeProb1(Map<String, Double> map){
//        System.out.println("Normalizing probs...");
        double sum = 0;
        for(String k: map.keySet()){
            sum+=map.get(k);
        }
        for(String k: map.keySet()){
            map.put(k, map.get(k)/sum);
        }
    }


    public static void main(String[] args) {
    }
}
