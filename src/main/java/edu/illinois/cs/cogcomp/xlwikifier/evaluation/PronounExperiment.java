package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.xlwikifier.*;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ctsai12 on 6/6/17.
 * Test pronoun resolution on ERE data
 */
public class PronounExperiment {

    public static void main(String[] args) {

        String config = "config/xlwikifier-tac.config";
        try {
            ConfigParameters.setPropValues(config);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        String ere_dir = "/shared/corpora/corporaWeb/tac/2016/ere-all/es-test/ere";
        String src_dir = "/shared/corpora/corporaWeb/tac/2016/ere-all/es-test/source";
        String lang = "es";

        List<QueryDocument> docs = EREReader.read(src_dir, ere_dir, lang);

        PronounCoreference pe = new PronounCoreference(lang);

        Map<String, Integer> prototal = new HashMap<>();
        Map<String, Integer> prohit = new HashMap<>();


        for(QueryDocument doc: docs){
            List<ELMention> pros = doc.mentions.stream().filter(x -> x.noun_type.equals("PRO")).collect(Collectors.toList());
            TextAnnotation ta = doc.getTextAnnotation();
            for(int i = 0; i < ta.getTokens().length; i++){
                String sur = ta.getToken(i).toLowerCase();
                if(pe.pro2type.containsKey(sur)){
                    IntPair offsets = ta.getTokenCharacterOffset(i);
                    Pair<Integer, Integer> xmloff = doc.getXmlhandler().getXmlOffsets(offsets.getFirst(), offsets.getSecond());
                    boolean match = false;
                    if(xmloff != null){
                        for(ELMention m: pros){
                            if(m.getStartOffset() == xmloff.getFirst() && m.getEndOffset() == xmloff.getSecond())
                                match = true;
                        }

                    }

                    if(!prototal.containsKey(sur)) {
                        prototal.put(sur, 0);
                        prohit.put(sur, 0);
                    }

                    prototal.put(sur, prototal.get(sur)+1);

                    if(match)
                        prohit.put(sur, prohit.get(sur)+1);
                }
            }
        }
        for(String pro: prototal.keySet()){
            System.out.println(pro+" "+prohit.get(pro)+" "+prototal.get(pro));
        }
        System.exit(-1);

        int total_cnt = 0, corr = 0;

        for(QueryDocument doc: docs){

            List<ELMention> authors = TACUtils.getDFAuthors(doc);
            List<ELMention> quote_authors = TACUtils.getQuoteAuthors(doc);

            doc.mentions = doc.mentions.stream().sorted(Comparator.comparingInt(ELMention::getStartOffset)).collect(Collectors.toList());

            for(ELMention m: doc.mentions){
                boolean a = false;
                for(ELMention au: authors){
                    if(au.getStartOffset() == m.getStartOffset() && au.getEndOffset() == m.getEndOffset()){
                        a = true;
                        break;
                    }
                }
                m.eazy = a;
            }

            for(int i = 0; i < doc.mentions.size(); i++){
                ELMention m = doc.mentions.get(i);
                String surface = m.getSurface().toLowerCase();
                if(m.noun_type.equals("PRO") && pe.pro2type.containsKey(surface)){
                    String type = pe.pro2type.get(surface);

                    if(!pe.I.contains(surface) && !pe.you.contains(surface))
                        continue;

                    ELMention prev_per = null, prev_nam = null, prev_po = null, prev_gpe = null;
                    ELMention prev_pro = null;
                    ELMention prev_author = null;

                    for(int j = i-1; j >= 0; j--){
                        ELMention pm = doc.mentions.get(j);
                        if(prev_pro == null && pm.noun_type.equals("NOM") && pm.getSurface().toLowerCase().equals(surface))
                            prev_pro = pm;

                        if(prev_author == null && pm.eazy)
                            prev_author = pm;

                        if(!pm.noun_type.equals("NAM")) continue;
                        if(prev_nam == null)
                            prev_nam = pm;
                        if(prev_per == null && pm.getType().equals("PER")) {
                            prev_per = pm;
                            if(prev_po == null)
                                prev_po = pm;
                        }
                        if(prev_po == null && pm.getType().equals("ORG")) {
                            if(prev_po == null)
                                prev_po = pm;
                        }
                        if(prev_gpe == null && pm.getType().equals("GPE"))
                            prev_gpe = pm;
                    }

                    ELMention prev_quote_author = null;
                    for(ELMention au: quote_authors)
                        if(au.getEndOffset() < m.getStartOffset() && prev_author!= null && au.getStartOffset() > prev_author.getEndOffset()){
                            for(int j = i-1; j >= 0; j--){
                                ELMention pm = doc.mentions.get(j);
                                if(pm.getSurface().equals(au.getSurface())){
                                    prev_quote_author = pm;
                                    break;
                                }
                            }
                            break;
                        }


                    if(pe.I.contains(surface) && prev_author!=null)
                        m.setMid(prev_author.gold_mid);
                    else if(pe.you.contains(surface) && prev_quote_author!=null) {
                        m.setMid(prev_quote_author.gold_mid);
                    }
                    else if(prev_per != null)
                        m.setMid(prev_per.gold_mid);
                    else if(prev_nam != null)
                        m.setMid(prev_nam.gold_mid);

//                    if(type.equals("PER") && prev_per != null)
//                        m.setMid(prev_per.gold_mid);
//                    else if(type.equals("GPE") && prev_gpe != null)
//                        m.setMid(prev_gpe.gold_mid);
//                    else if(type.equals("PO") && prev_po != null)
//                        m.setMid(prev_po.gold_mid);
//                    else if(prev_nam != null)
//                        m.setMid(prev_nam.gold_mid);
//                    else{
//
//                    }

                    total_cnt++;
                    if(m.getMid().equals(m.gold_mid))
                        corr++;

                }
            }
        }

        System.out.println(corr+" "+total_cnt);

    }
}
