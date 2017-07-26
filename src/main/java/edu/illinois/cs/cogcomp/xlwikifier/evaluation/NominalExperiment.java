package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Created by ctsai12 on 6/1/17.
 */
public class NominalExperiment {

    public static void main(String[] args) {

        try {
            ConfigParameters.setPropValues("config/xlwikifier-tac.config");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        TACDataReader reader = new TACDataReader(true);
        List<ELMention> golds = reader.read2016SpanishEvalGoldNAMNOM();

        Map<String, Integer> total_cnt = new HashMap<>();
        Map<String, Integer> prev_corr = new HashMap<>();
        Map<String, Integer> next_corr = new HashMap<>();
        Map<String, Integer> close_corr = new HashMap<>();
        Map<String, Integer> prev_nom_total = new HashMap<>();
        Map<String, Integer> prev_nom_corr = new HashMap<>();

        String[] types = {"LOC","GPE","PER","ORG","FAC"};
        for(String type: types){
            total_cnt.put(type, 0);
            prev_corr.put(type, 0);
            next_corr.put(type, 0);
            close_corr.put(type, 0);
            prev_nom_total.put(type, 0);
            prev_nom_corr.put(type, 0);
        }


        Map<String, List<ELMention>> doc2golds = golds.stream().collect(groupingBy(x -> x.getDocID()));
        for(String docid: doc2golds.keySet()){
            List<ELMention> dg = doc2golds.get(docid).stream()
                    .sorted(Comparator.comparingInt(ELMention::getStartOffset))
                    .collect(Collectors.toList());
            for(int i=0; i < dg.size(); i++){
                ELMention target = dg.get(i);
                if(target.noun_type.equals("NOM")){
                    String type = target.getType();
                    boolean get = false;
//                    for(int j = i-1; j >=0; j--){
//                        ELMention m = dg.get(j);
//                        if(m.getSurface().toLowerCase().equals(target.getSurface().toLowerCase())){
//                            get = true;
//                            target.setMid(m.getMid());
//                            prev_nom_total.put(type, prev_nom_total.get(type)+1);
//                            if(m.getMid().equals(target.gold_mid)){
//                                prev_nom_corr.put(type, prev_nom_corr.get(type)+1);
//                            }
//                        }
//                    }

                    if(get) continue;
                    total_cnt.put(type, total_cnt.get(type)+1);

                    ELMention prev_ne = null, next_ne = null, close_ne = null;
                    for(int j = i-1; j >=0; j--){
                        ELMention m = dg.get(j);
                        if(m.noun_type.equals("NAM") && m.getType().equals(type)){
                            prev_ne = m;
                            break;
                        }
                    }

                    for(int j = i+1; j < dg.size(); j++){
                        ELMention m = dg.get(j);
                        if(m.noun_type.equals("NAM") && m.getType().equals(type)){
                            next_ne = m;
                            break;
                        }
                    }

                    if(prev_ne != null){
                        if(prev_ne.gold_mid.equals(target.gold_mid))
                            prev_corr.put(type, prev_corr.get(type)+1);
                    }

                    if(next_ne != null){
                        if(next_ne.gold_mid.equals(target.gold_mid))
                            next_corr.put(type, next_corr.get(type)+1);
                    }

                    close_ne = prev_ne;
                    if(next_ne != null){
                        if(close_ne != null && ((next_ne.getStartOffset() - target.getEndOffset()) < (target.getStartOffset() - prev_ne.getEndOffset())))
                            close_ne = next_ne;
                    }
                    if(close_ne != null) {
                        target.setMid(close_ne.gold_mid);
                        if (close_ne.gold_mid.equals(target.gold_mid))
                            close_corr.put(type, close_corr.get(type) + 1);
                    }

                }
            }
        }

        int total = 0, correct = 0, pn_total = 0, pn_corr = 0;
        for(String type: types){
            total += total_cnt.get(type);
            correct += close_corr.get(type);
            System.out.println(type+"\t"+total_cnt.get(type)+"\t"+prev_corr.get(type)+"\t"+next_corr.get(type)+"\t"+close_corr.get(type));
            System.out.println(type+"\t"+prev_nom_total.get(type)+"\t"+prev_nom_corr.get(type));
            pn_total += prev_nom_total.get(type);
            pn_corr += prev_nom_corr.get(type);
        }

        System.out.println(correct +"\t"+total+"\t"+((double)correct)/total);
        System.out.println(pn_corr +"\t"+pn_total+"\t"+((double)pn_corr)/pn_total);

    }
}
