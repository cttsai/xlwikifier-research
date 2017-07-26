package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.TitleGender;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ctsai12 on 6/6/17.
 * Adding extracted pronouns into the existing name clusters.
 * Last step of the Cold Start evaluation
 * Assume wikified named entities (from xlwikifier) and pronouns (from C++ code)
 * Both are in the TAC submission format
 */
public class PronounCoreference {

    public Map<String, String> pro2type = new HashMap<>();
    public Set<String> males = new HashSet<>();
    public Set<String> females = new HashSet<>();
    public Set<String> I = new HashSet<>();
    public Set<String> you = new HashSet<>();

    public TitleGender tg;

    /**
     * Read pronoun lists of the target language
     * Note: only "I" and "you" lists are used now
     * @param lang
     */
    public PronounCoreference(String lang){

        tg = TitleGender.getTitleGender("en");

        String dir = "/shared/corpora/ner/pronominal_exp/"+lang+"/";

        try {
            ArrayList<String> lines = LineIO.read(dir + "I");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PER");
                I.add(line);
            }
            lines = LineIO.read(dir + "you");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PER");
                you.add(line);
            }
            lines = LineIO.read(dir + "PER");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PER");
            }
            lines = LineIO.read(dir + "male");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PER");
                males.add(line);
            }
            lines = LineIO.read(dir + "female");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PER");
                females.add(line);
            }
            lines = LineIO.read(dir + "ALL");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "ALL");
            }
            lines = LineIO.read(dir + "PO");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "PO");
            }
            lines = LineIO.read(dir + "GPE");
            for(String line: lines){
                line = line.trim().toLowerCase().split("\t")[0];
                pro2type.put(line, "GPE");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main logic. Add pronouns into the current mention clusters
     * @param doc
     * @param pronouns
     */
    public void pronounCoref(QueryDocument doc, List<ELMention> pronouns){

        for(ELMention pro: pronouns){
            boolean over = false;
            for(ELMention m: doc.mentions){
                if((pro.getStartOffset() >= m.getStartOffset() && pro.getStartOffset() <= m.getEndOffset())
                        || (pro.getEndOffset() >= m.getStartOffset() && pro.getEndOffset() <= m.getEndOffset())) {
                    over = true;
                    break;
                }
            }
            if(!over)
                doc.mentions.add(pro);
        }

        doc.mentions = doc.mentions.stream().sorted(Comparator.comparingInt(ELMention::getStartOffset)).collect(Collectors.toList());

        List<ELMention> authors = TACUtils.getDFAuthors(doc);
        List<ELMention> quote_authors = TACUtils.getQuoteAuthors(doc);
//        System.out.println(quote_authors.size());

        int cnt = 0;
        for(ELMention m: doc.mentions){
            boolean a = false;
            for(ELMention au: authors){
                // NOTE: the offset is inclusive in the input file!!
                if(au.getStartOffset() == m.getStartOffset() && (au.getEndOffset()-1) == m.getEndOffset()){
                    cnt++;
                    a = true;
                    break;
                }
            }
            m.eazy = a;
        }
//        System.out.println("# matched authors "+cnt);

        for(int i = 0; i < doc.mentions.size(); i++) {
            ELMention m = doc.mentions.get(i);
            String surface = m.getSurface().toLowerCase();
            if (m.noun_type.equals("PRO")) {
                String type = m.getType();

                ELMention prev_nam = null, prev_author = null, prevprev_author = null;
                ELMention prev_male = null, prev_female = null;

                for (int j = i - 1; j >= 0; j--) {
                    ELMention pm = doc.mentions.get(j);
                    if(pm == null) continue;
                    if(prev_author != null && prevprev_author == null && pm.eazy)
                        prevprev_author = pm;
                    if (prev_author == null && pm.eazy)
                        prev_author = pm;


                    if (prev_nam == null && !pm.eazy && pm.noun_type.equals("NAM") && pm.getType().equals(m.getType()))
                        prev_nam = pm;

                    if (prev_male == null && !pm.eazy && pm.noun_type.equals("NAM") && pm.getType().equals("PER")){
                        if(!pm.getEnWikiTitle().startsWith("NIL")){
                            boolean male = tg.getGender(pm.getEnWikiTitle());
                            if(male)
                                prev_male = pm;
                        }
                    }
                    if (prev_female == null && !pm.eazy && pm.noun_type.equals("NAM") && pm.getType().equals("PER")){
                        if(!pm.getEnWikiTitle().startsWith("NIL")){
                            boolean male = tg.getGender(pm.getEnWikiTitle());
                            if(!male)
                                prev_female = pm;
                        }
                    }
                }

                ELMention next_author = null;
                for (int j = i + 1; j < doc.mentions.size(); j++) {
                    ELMention nm = doc.mentions.get(j);
                    if (nm == null) continue;
                    if (nm.eazy){
                        next_author = nm;
                        break;
                    }
                }

                ELMention quote_author = null;
                for (ELMention au : quote_authors)
                    if(prev_author != null && au.getStartOffset() > prev_author.getEndOffset()){
                        if(next_author != null && next_author.getEndOffset() < au.getStartOffset())
                            break;
                        for (int j = i - 1; j >= 0; j--) {
                            ELMention pm = doc.mentions.get(j);
                            if(pm == null) continue;
                            if (pm.getSurface().equals(au.getSurface())) {
                                quote_author = pm;
                                break;
                            }
                        }
                        break;
                    }

//                if(prev_author != null)
//                    System.out.println("  prev author "+prev_author.getSurface());
//                if(prevprev_author != null)
//                    System.out.println("  prevprev author "+prevprev_author.getSurface());
//                if(prev_nam != null)
//                    System.out.println("  prev nam "+prev_nam.getSurface());
//                if(prev_male != null)
//                    System.out.println("  prev male "+prev_male.getSurface());
//                if(prev_female != null)
//                    System.out.println("  prev female "+prev_female.getSurface());

                // resolve I and you to the post authors
                if(I.contains(surface) || you.contains(surface)) {
                    if (I.contains(surface) && prev_author != null) {
                        m.setMid(prev_author.getMid());
                        m.confidence = prev_author.confidence;
                    }
                    else if (you.contains(surface) && quote_author != null) {
                        m.setMid(quote_author.getMid());
                        m.confidence = quote_author.confidence;
                    }
                    else if (you.contains(surface) && prevprev_author != null) {
                        m.setMid(prevprev_author.getMid());
                        m.confidence = prevprev_author.confidence;
                    }
                    else if (prev_nam != null) {
                        m.setMid(prev_nam.getMid());
                        m.confidence = prev_nam.confidence;
                    }
                    else
                        doc.mentions.set(i, null);
                }
                else { // resolve 3rd person pronoun to the previous mention other than the poster and the poster in quotes

                    if(m.getType().equals("PER") && prev_nam != null){
                        if(males.contains(surface) && prev_male != null && prev_male.getStartOffset() >= prev_nam.getStartOffset()) {
                            m.setMid(prev_male.getMid());
                            m.confidence = prev_male.confidence;
                        }
                        else if(females.contains(surface) && prev_female != null && prev_female.getStartOffset() >= prev_nam.getStartOffset()) {
                            m.setMid(prev_female.getMid());
                            m.confidence = prev_female.confidence;
                        }
                        else {
                            m.setMid(prev_nam.getMid());
                            m.confidence = prev_nam.confidence;
                        }
                    }
                    else if(prev_nam != null) {
                        m.setMid(prev_nam.getMid());
                        m.confidence = prev_nam.confidence;
                    }
                    else{
                        doc.mentions.set(i, null);
                    }
                }

//                System.out.println(m.getSurface() + " " + m.getMid());
            }
        }

        doc.mentions = doc.mentions.stream().filter(x -> x!=null).collect(Collectors.toList());
    }

    public static void main(String[] args) {

//        String input = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/en/TAC2017.en.eval.nam";
//        Map<String, List<ELMention>> mss = NominalCoreference.readSubmissionFormat(input);
//        String output = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/en/2017/UIUC_EDL_ENG_ere_v2";
//        String output1 = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/en/2017/UIUC_EDL_ENG_cold_start_v2";
//        List<ELMention> msss = mss.entrySet().stream().flatMap(x -> x.getValue().stream())
//                .filter(x -> !x.getSurface().contains("\""))
//                .filter(x -> !x.getType().equals("TTL"))
//                .collect(Collectors.toList());
//        NominalCoreference.printEREFormat(msss, output);
//        NominalCoreference.printColdStartFormat(msss, output1);
//        System.exit(-1);
        try {
            ConfigParameters.setPropValues();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        PronounCoreference pe = new PronounCoreference("es");
        TACDataReader reader = new TACDataReader(false);

        // The original docs are needed because we need to know which mentions are author names
        List<QueryDocument> docs = null;
        try {
//            docs = reader.readDocs("/shared/corpora/corporaWeb/tac/LDC2016E63_TAC_KBP_2016_Evaluation_Source_Corpus_V1.1/data/spa/df/", "es");
//            docs.addAll(reader.readDocs("/shared/corpora/corporaWeb/tac/LDC2016E63_TAC_KBP_2016_Evaluation_Source_Corpus_V1.1/data/spa/nw/", "es"));
            docs = reader.readDocs("/shared/corpora/corporaWeb/tac/2017/LDC2017E25_TAC_KBP_2017_Evaluation_Source_Corpus/data/spa/nw/", "es");
            docs.addAll(reader.readDocs("/shared/corpora/corporaWeb/tac/2017/LDC2017E25_TAC_KBP_2017_Evaluation_Source_Corpus/data/spa/df/", "es"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Read named entity/nominal mentions
//        Map<String, List<ELMention>> mentions = NominalCoreference.readSubmissionFormat("/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2016.es.nam.nom.nwauthor");
        Map<String, List<ELMention>> mentions = NominalCoreference.readSubmissionFormat("/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2017.es.nam.nom.fix.nwauthor");
        for(QueryDocument doc: docs){
//            if(!doc.getDocID().equals("SPA_NW_001278_20130812_F000154CF")) continue;
            if(mentions.containsKey(doc.getDocID()))
                doc.mentions = mentions.get(doc.getDocID());
        }

        // Read pronouns mentions
//        String profile = "/home/ctsai12/CLionProjects/NER/cmake-build-debug/tac2016.spanish.pro.sub";
		String profile = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2017.es.pro.fix";
        Map<String, List<ELMention>> pros = NominalCoreference.readSubmissionFormat(profile);


        for(QueryDocument doc: docs){
//            if(!doc.getDocID().equals("SPA_NW_001278_20130812_F000154CF")) continue;
            if(pros.containsKey(doc.getDocID()))
                pe.pronounCoref(doc, pros.get(doc.getDocID()));
        }

        String outdir = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/2017/";
        List<ELMention> ms = docs.stream().flatMap(x -> x.mentions.stream())
                .filter(x -> !x.getSurface().contains("\""))
                .collect(Collectors.toList());

        NominalCoreference.printColdStartFormat(ms, outdir+"UIUC_EDL_SPA_cold_start_v4");
        NominalCoreference.printEREFormat(ms, outdir+"UIUC_EDL_SPA_ere_v4");
//        NominalCoreference.printSubmissionFormat(ms, outdir+"UIUC_EDL_SPA_tac_v1");
    }
}
