package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * Created by ctsai12 on 6/2/17.
 * Adding extracted nominal mentions into name clusters.
 * The name clusters are from cross-lingual wikifier.
 * Nominals are extracted by the C++ codes.
 * All inputs are in the TAC submission format.
 * Many I/O functions are in this class.
 */
public class NominalCoreference {

    public static  Map<String, List<ELMention>> readSubmissionFormat(String file){

        System.out.println("reading "+file);

        List<String> lines = null;
        try {
            lines = LineIO.read(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Map<String, List<ELMention>> id2ms = new HashMap<>();

        int nskip = 0;
        for(String line: lines){
            String[] parts = line.split("\t");
            if(parts.length < 7) {
                nskip++;
                continue;
            }
            String surface = parts[2];
            String[] ps = parts[3].split(":");
            if(ps.length < 2)
                continue;
            String docid = ps[0];
            String[] ss = ps[1].split("-");
            int start = Integer.parseInt(ss[0]);
            int end = Integer.parseInt(ss[1]);
            ELMention m = new ELMention(docid, start, end);
            m.setType(parts[5]);
            m.noun_type = parts[6];
            m.setSurface(surface);
            if(!parts[4].equals("x"))
                m.setMid(parts[4]);

            m.confidence = Double.parseDouble(parts[7]);


            if(parts.length > 8)
                m.setEnWikiTitle(parts[8]);
                    ;
            if(!id2ms.containsKey(docid))
                id2ms.put(docid, new ArrayList<>());

            id2ms.get(docid).add(m);
        }

        System.out.println(id2ms.size());
        System.out.println("skipped "+nskip+" lines");
        return id2ms;
    }

    /**
     * Add nominals into named entity clusters
     * @param nams
     * @param noms
     * @return
     */
    public static List<ELMention> addNOM(Map<String, List<ELMention>> nams, Map<String, List<ELMention>> noms){

        boolean use_window = true;
        boolean remove = true;
        int window = 300;
        int nil = 999999999;
        List<ELMention> newm = new ArrayList<>();
        for(String docid: nams.keySet()){

            if(!noms.containsKey(docid)){
                newm.addAll(nams.get(docid));
                continue;
            }

            List<ELMention> nes = nams.get(docid).stream().sorted(Comparator.comparingInt(ELMention::getStartOffset)).collect(Collectors.toList());
            List<ELMention> nos = noms.get(docid).stream().sorted(Comparator.comparingInt(ELMention::getStartOffset)).collect(Collectors.toList());

            for(ELMention no: nos){
                boolean over = false;
                for(ELMention ne: nes){
                    if((no.getStartOffset() >= ne.getStartOffset() && no.getStartOffset() <= ne.getEndOffset())
                            || (no.getEndOffset() >= ne.getStartOffset() && no.getEndOffset() <= ne.getEndOffset())) {
                        over = true;
                        break;
                    }
                }
                if(!over)
                    nes.add(no);
            }

            nes = nes.stream().sorted(Comparator.comparingInt(ELMention::getStartOffset)).collect(Collectors.toList());


            for(int i = 0; i < nes.size(); i++) {
                ELMention m = nes.get(i);
                if (!m.noun_type.equals("NOM")) continue;
                m.setMid("NIL");
                ELMention prev_nam = null, prev_nom = null;
                for (int j = i - 1; j >= 0; j--) {
                    ELMention pm = nes.get(j);
                    if (pm == null) continue;
                    if (prev_nam == null && pm.noun_type.equals("NAM") && pm.getType().equals(m.getType())) {
                        if (!use_window || m.getStartOffset() - pm.getEndOffset() < window)
                            prev_nam = pm;
                    }
                    if (prev_nom == null && pm.noun_type.equals("NOM") && pm.getType().equals(m.getType())
                            && pm.getSurface().toLowerCase().equals(m.getSurface().toLowerCase())) {
                        if (!use_window || m.getStartOffset() - pm.getEndOffset() < window) {
                            prev_nom = pm;
                        }
                    }
                }

                ELMention next_nam = null;
                for(int j = i+1; j<nes.size(); j++){
                    ELMention pm = nes.get(j);
                    if (next_nam == null && pm.noun_type.equals("NAM") && pm.getType().equals(m.getType())) {
                        if (!use_window || m.getStartOffset() - pm.getEndOffset() < window)
                            next_nam = pm;
                    }
                }

                ELMention closest = prev_nam;
                if(next_nam != null){
                    if(closest == null || next_nam.getStartOffset() - m.getEndOffset() < m.getStartOffset() - prev_nam.getEndOffset())
                        closest = next_nam;
                }

                if(closest != null){
                    m.setMid(closest.getMid());
                    // don't know why 0.9, just feel not confident with nominals
                    m.confidence = closest.confidence*0.9;
                } else {
                    if (remove) {
                        nes.set(i, null);
                    } else {
                        m.setMid("NIL" + nil);
                        nil--;
                    }
                }
            }

            for(ELMention m: nes){
                if(m != null)
                    newm.add(m);
            }
        }

        return newm;
    }

    /**
     * For running the evaluation script
     */
    public static void printEvalFormat(List<ELMention> mentions, String outfile){

        try {
            BufferedWriter bwall = new BufferedWriter(new FileWriter(outfile+".all"));
            BufferedWriter bwnam = new BufferedWriter(new FileWriter(outfile+".nam"));
            BufferedWriter bwnom = new BufferedWriter(new FileWriter(outfile+".nom"));
            for(ELMention m: mentions){
                bwall.write(m.getDocID()+"\t"+m.getStartOffset()+"\t"+m.getEndOffset()+"\t"+m.getMid()+"\t0\t"+m.getType()+"\n");
                if(m.noun_type.equals("NAM"))
                    bwnam.write(m.getDocID()+"\t"+m.getStartOffset()+"\t"+m.getEndOffset()+"\t"+m.getMid()+"\t0\t"+m.getType()+"\n");
                if(m.noun_type.equals("NOM"))
                    bwnom.write(m.getDocID()+"\t"+m.getStartOffset()+"\t"+m.getEndOffset()+"\t"+m.getMid()+"\t0\t"+m.getType()+"\n");
            }
            bwall.close();
            bwnam.close();
            bwnom.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public static void printSubmissionFormat(List<ELMention> mentions, String outfile){

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
            int cnt = 0;
            for(ELMention m: mentions){
                bw.write("UI_CCG\t"+cnt+"\t"+m.getSurface()+"\t"+m.getDocID()+":"+m.getStartOffset()+"-"+m.getEndOffset()+"\t"+m.getMid()+"\t"+m.getType()+"\t"+m.noun_type+"\t"+m.confidence+"\t"+m.getEnWikiTitle()+"\n");
                cnt++;
            }

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void reassignNIL(Map<String, List<ELMention>> id2ms){
        int nil = 1;

        for(String docid: id2ms.keySet()){

            Map<String, List<ELMention>> mid2ms = id2ms.get(docid).stream().collect(groupingBy(x -> x.getMid()));

            List<ELMention> newm = new ArrayList<>();
            for(String mid: mid2ms.keySet()){
                if(mid.startsWith("NIL")){
                    for(ELMention m: mid2ms.get(mid)) {
                        m.setMid("NIL" + nil);
                        newm.add(m);
                    }
                    nil++;
                }
                else
                    newm.addAll(mid2ms.get(mid));
            }
            id2ms.put(docid, newm);
        }
    }

    public static void printEREFormat(List<ELMention> mentions, String outdir){
        File dir = new File(outdir);
        if(!dir.exists())
            dir.mkdir();
        else
            dir.delete();

        Map<String, List<ELMention>> doc2mens = mentions.stream().collect(groupingBy(x -> x.getDocID()));
        for(String docid: doc2mens.keySet()) {
            try {

                BufferedWriter bw = new BufferedWriter(new FileWriter(outdir+"/"+docid+".rich_ere.xml"));

                bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                bw.write("<deft_ere kit_id=\"kit_id\" doc_id=\""+docid+"\" source_type=\"multi_post\">\n");
                bw.write("\t<entities>\n");

                Map<String, List<ELMention>> mid2ms = doc2mens.get(docid).stream().collect(groupingBy(x -> x.getMid()));
                int entity_cnt = 0, mention_cnt = 0;
                for (String mid : mid2ms.keySet()) {
                    List<ELMention> ms = mid2ms.get(mid).stream()
                            .sorted((x1, x2) -> Integer.compare(x2.getSurface().length(), x1.getSurface().length()))
                            .collect(Collectors.toList());
                    ELMention cano = ms.get(0);
                    bw.write("\t\t<entity id=\"ent-"+entity_cnt+"\" type=\""+cano.getType()+"\" specificity=\"specific\" kb_id=\""+mid+"\">\n");

                    for(ELMention m: ms){
                        bw.write("\t\t\t<entity_mention id=\"m-"+mention_cnt+"\" noun_type=\""+m.noun_type+"\" source=\""+docid+"\" offset=\""+m.getStartOffset()+"\" length=\""+(m.getEndOffset()-m.getStartOffset()+1)+"\">\n");
                        bw.write("\t\t\t\t<mention_text>"+m.getSurface()+"</mention_text>\n");
                        bw.write("\t\t\t</entity_mention>\n");
                        mention_cnt++;
                    }

                    bw.write("\t\t</entity>\n");
                    entity_cnt++;
                }

                bw.write("\t</entities>\n");
                bw.write("</deft_ere>");
                bw.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void printColdStartFormat(List<ELMention> mentions, String outfile){

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(outfile));
            bw.write("UIUC_CCG\n\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        int entity_cnt = 0;

        Map<String, List<ELMention>> mid2ms = mentions.stream().collect(groupingBy(x -> x.getMid()));
        for (String mid : mid2ms.keySet()) {
            Map<String, List<ELMention>> doc2mens = mid2ms.get(mid).stream().collect(groupingBy(x -> x.getDocID()));
            List<Map.Entry<String, Long>> typecnt = mid2ms.get(mid).stream().map(x -> x.getType()).collect(groupingBy(x -> x, counting()))
                    .entrySet().stream().sorted((x1, x2) -> Long.compare(x2.getValue(), x1.getValue()))
                    .collect(Collectors.toList());
            String entype = typecnt.get(0).getKey();
            try {
                bw.write(":Entity_" + entity_cnt + "\ttype\t" + entype + "\n");
                for(String docid: doc2mens.keySet()) {

                    List<ELMention> ms = doc2mens.get(docid).stream()
                            .sorted((x1, x2) -> Integer.compare(x2.getSurface().length(), x1.getSurface().length()))
                            .collect(Collectors.toList());
                    ELMention cano = null;
                    for(ELMention m: ms)
                        if(cano == null && m.noun_type.equals("NAM"))
                            cano = m;
                    for(ELMention m: ms)
                        if(cano == null && m.noun_type.equals("NOM"))
                            cano = m;
                    for(ELMention m: ms)
                        if(cano == null && m.noun_type.equals("PRO"))
                            cano = m;
                    bw.write(":Entity_" + entity_cnt + "\tcanonical_mention\t\"" + cano.getSurface() + "\"\t" + cano.getDocID() + ":" + cano.getStartOffset() + "-" + cano.getEndOffset() + "\t" + cano.confidence + "\n");
                    for (ELMention m : ms) {
                        String mtype = "mention";
                        if (m.noun_type.equals("NOM"))
                            mtype = "nominal_mention";
                        else if (m.noun_type.equals("PRO"))
                            mtype = "pronominal_mention";
                        bw.write(":Entity_" + entity_cnt + "\t" + mtype + "\t\"" + m.getSurface() + "\"\t" + m.getDocID() + ":" + m.getStartOffset() + "-" + m.getEndOffset() + "\t" + m.confidence + "\n");
                    }
                    bw.write(":Entity_" + entity_cnt + "\tlink\tLDC2015E42:" + cano.getMid() + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            entity_cnt++;
        }

        try {
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Select the docs which we have golds. Remove prnouns cause we don't have golds for them.
     * Print the results in the format which can be evaluated by the official python script
     * @param infile
     */
    public static void printEvalDocs(String infile, String outfile){

        Map<String, List<ELMention>> doc2mens = readSubmissionFormat(infile);

        List<ELMention> mentions = doc2mens.entrySet().stream().flatMap(x -> x.getValue().stream())
                .filter(x -> !x.noun_type.equals("PRO"))
                .collect(Collectors.toList());

        String goldfile = "/shared/preprocessed/ctsai12/multilingual/deft/data/tac2016.eval.golds";
        Map<String, List<ELMention>> golds = readSubmissionFormat(goldfile);
        Set<String> gold_docid = golds.keySet();

        List<ELMention> em = mentions.stream().filter(x -> gold_docid.contains(x.getDocID())).collect(Collectors.toList());

        outfile = "/shared/experiments/ctsai12/workspace/xlwikifier-demo/tac-eval/"+outfile;
        printEvalFormat(em, outfile);
    }

    public static void main(String[] args) {

//        namfile = "/shared/experiments/ctsai12/workspace/xlwikifier-demo/TAC2016.es.all.conf";
        String namfile = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2017.es.nam";
        //String nomfile = "/home/ctsai12/CLionProjects/NER/cmake-build-debug/tac2016.spanish.nom.sub";
        String nomfile = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2017.es.nom.fix";

        Map<String, List<ELMention>> nams = readSubmissionFormat(namfile);
        //Map<String, List<ELMention>> nams1 = readSubmissionFormat(namfile1);

        // for the small eval docs, somehow the results from only running on these docs are better
		/*
        for(String docid: nams1.keySet()){
            nams.put(docid, nams1.get(docid));
        }
		*/
        reassignNIL(nams); // because the eval corpus is splitted into 4 parts
        Map<String, List<ELMention>> noms = readSubmissionFormat(nomfile);

        // main logic
        List<ELMention> mentions = addNOM(nams, noms);

        printSubmissionFormat(mentions, "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2017.es.nam.nom.fix");

//        String subfile = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/NAM_NOM.tac.conf";
//        String subfile = "/shared/bronte/Tinkerbell/EDL/cold_start_outputs/es/TAC2016.es.test";
//        String outfile = "es.test";
//        printEvalDocs(subfile, outfile);

    }
}
