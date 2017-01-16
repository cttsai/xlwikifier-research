package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;


import edu.illinois.cs.cogcomp.core.datastructures.Pair;

import java.util.*;

import static java.util.stream.Collectors.joining;

/**
 * Created by ctsai12 on 9/28/16.
 */
public class TitlePair {

    public String[] src_title;
    public String[] tgt_title;

    public String lm;
	public String l;
	public String m;

    public Map<String, List<Pair<String, String>>> align2pairs;

    public Map<String, List<List<Pair<String, String>>>> align2phrasepairs;

    public TitlePair(Pair<String[], String[]> pair){
        src_title = pair.getFirst();
        tgt_title = pair.getSecond();
        lm = tgt_title.length+"_"+src_title.length;
		l = String.valueOf(tgt_title.length);
		m = String.valueOf(src_title.length);
    }

    public Pair<String[], String[]> getPartsPair(){
       return new Pair<>(src_title, tgt_title);
    }

    public void populateAllAssignments(){
        int l = tgt_title.length;
        int m = src_title.length;
        // (m+1)^l
        List<List<Integer>> assignments = TransUtils.getAllAssignments(l, m);  // m+1: adding null alignment
//        List<List<Integer>> assignments = TransUtils.getAllAssignments(m, l+1); // (l+1)^m
//        List<List<Integer>> assignments = TransUtils.getAllAssignments(m, l);
        // each word in src can be linked to multiple tgt words

        align2pairs = new HashMap<>();
        for (List<Integer> assign : assignments) {

            String akey = assign.stream().map(x -> String.valueOf(x)).collect(joining("_"));

            List<Pair<String, String>> wordpairs = new ArrayList<>();


            Set<Integer> used_tgt = new HashSet<>();
            boolean bad = false;
            // source word
            for (int j = 0; j < m; j++) {
                String targetwords = "";
                int n_t = 0;

                // find the aligned tgt words
                for (int i = 0; i < l; i++) {
                    if (assign.get(i) == j) {
                        if(used_tgt.contains(i)){
                            bad = true;
                        }

                        used_tgt.add(i);
                        targetwords += tgt_title[i]+" ";
                        n_t++;
                    }
                }

                targetwords = targetwords.trim();
                if(!targetwords.isEmpty() && (n_t == 1)){// || (n_t == 2 && targetwords.length() < 10))) {
                    wordpairs.add(new Pair<>(src_title[j], targetwords));
                }
            }


            if(wordpairs.size() == m)
//            if(!bad)
                align2pairs.put(akey, wordpairs);
        }
    }

    public void populateAllAssignments1(){
        int l = tgt_title.length;
        int m = src_title.length;
        // (m+1)^l
        List<List<Integer>> assignments = TransUtils.getAllAssignments(l, m);  // m+1: adding null alignment

        align2phrasepairs = new HashMap<>();
        for (List<Integer> assign : assignments) {


            List<Pair<String, String>> wordpairs = new ArrayList<>();
            Set<Integer> used_tgt = new HashSet<>();
            boolean bad = false;
            // source word
            for (int j = 0; j < m; j++) {
                String targetwords = "";
                int n_t = 0;

                // find the aligned tgt words
                for (int i = 0; i < l; i++) {
                    if (assign.get(i) == j) {
						if(TitleTranslator.lang.equals("zh"))
                        	targetwords += tgt_title[i];
						else
                        	targetwords += tgt_title[i]+" ";
                        n_t++;
                    }
                }

				if(n_t > 1){
					wordpairs.clear();
					break;
				}

                targetwords = targetwords.trim();
                if(!targetwords.isEmpty() && (n_t == 1)){//|| (n_t == 2 && targetwords.length() < 10)) ){
                    wordpairs.add(new Pair<>(src_title[j], targetwords));
                }
            } // end source

            if(wordpairs.size() > 0 && (TransUtils.all_length || wordpairs.size() == m))
			{
				String akey = assign.stream().filter(x -> x < m)
					.map(x -> String.valueOf(x)).collect(joining("_"));
				if(!akey.isEmpty()){
					if(!align2phrasepairs.containsKey(akey))
						align2phrasepairs.put(akey, new ArrayList());
					align2phrasepairs.get(akey).add(wordpairs);
				}
			}
        }
    }
}
