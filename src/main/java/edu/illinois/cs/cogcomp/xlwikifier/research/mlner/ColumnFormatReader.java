package edu.illinois.cs.cogcomp.xlwikifier.research.mlner;

import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Created by ctsai12 on 3/2/16.
 */
public class ColumnFormatReader {

    public List<TextAnnotation> tas;
    public List<QueryDocument> docs;
    public List<List<String>> wikifier_features;
    private static Logger logger = LoggerFactory.getLogger(ColumnFormatReader.class);
    private String mention = null, type = null;
    private int mention_start = -1, mention_end = -1;
    private int mention_xml_start = -1, mention_xml_end = -1;

    public ColumnFormatReader(){

    }

    public void loadDryRun(String set){

        if(set.equals("0")){
            readDir("/shared/corpora/ner/dryrun/set0/mono", false);
        }
        else if(set.equals("E")){
            readDir("/shared/corpora/ner/dryrun/setE-new", false);
        }
        else{
            System.out.println("Wrong set name in dry run: "+set);
            System.exit(-1);
        }
    }


    public void loadTacTrain(String lang){
        if(lang.equals("zh"))
            readDir("/shared/corpora/ner/tac/zh/train2-4types", true);
        if(lang.equals("es"))
            readDir("/shared/corpora/ner/tac/es/train-4types", true);
        if(lang.equals("en"))
            readDir("/shared/corpora/ner/tac/en/train-4types", true);
    }

    public void loadTacTest(String lang){
        if(lang.equals("zh"))
            readDir("/shared/corpora/ner/tac/zh/test2-4types", true);
        if(lang.equals("es"))
            readDir("/shared/corpora/ner/tac/es/test-4types", true);
        if(lang.equals("en"))
            readDir("/shared/corpora/ner/tac/en/test-4types", true);

    }




    public void loadOntoNotes(String part){
        if(part.equals("traindev")){
            readDir("/shared/corpora/ner/ontonotes/ColumnFormat/TrainAndDev", false);
        }
        else if(part.equals("test")){
            readDir("/shared/corpora/ner/ontonotes/ColumnFormat/Test", false);
        }
    }

    public void loadHengJiTrain(String lang){
        String dir = "/shared/corpora/ner/hengji/";
        readDir(dir+lang+"/Train", true);
    }

    public void loadHengJiTest(String lang){
        String dir = "/shared/corpora/ner/hengji/";
        readDir(dir+lang+"/Test", true);
    }


    private TextAnnotation createTextAnnotation(String text, List<IntPair> offsets, List<String> surfaces, List<Integer> sen_ends, String id){

        IntPair[] offs = new IntPair[offsets.size()];
        offs = offsets.toArray(offs);
        String[] surfs = new String[surfaces.size()];
        surfs = surfaces.toArray(surfs);
        int[] ends = new int[sen_ends.size()];
        for(int i = 0; i < sen_ends.size(); i++)
            ends[i] = sen_ends.get(i);

        if(ends[ends.length-1]!=surfaces.size()) {
            System.out.println(ends[ends.length - 1]);
            System.out.println(surfaces.size());
            System.exit(-1);
        }

        TextAnnotation ta = new TextAnnotation("", id, text, offs,
                surfs, ends);
        return ta;

    }

    private void resetVars(){
        mention = null;
        type = null;
        mention_start = -1;
        mention_end = -1;
        mention_xml_start = -1;
        mention_xml_end = -1;
    }

    private ELMention createMention(String docid){
        ELMention m = new ELMention(docid, mention_start, mention_end);
        m.setSurface(mention);
        m.setType(type);
        resetVars();
        return m;
    }

    public QueryDocument readFile(File f){
        String docid = f.getName();
        List<String> features = new ArrayList<>();
        List<IntPair> offsets = new ArrayList<>();
        List<String> surfaces = new ArrayList<>();
        List<Integer> sen_ends = new ArrayList<>();
        String text = "";
        List<ELMention> mentions = new ArrayList<>();
        List<String> lines = null;
        resetVars();
        try {
            lines = LineIO.read(f.getAbsolutePath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        for(String line: lines){
//            System.out.println(line);
            if(line.contains("-DOCSTART-")) continue;
            if(line.trim().isEmpty()){
                if(surfaces.size()>0 && (sen_ends.size() == 0 || surfaces.size()!= sen_ends.get(sen_ends.size()-1)))
                    sen_ends.add(surfaces.size());
                if(mention != null && mention_start!=-1 && mention_end!=-1){
//                    System.out.println("add");
                    mentions.add(createMention(docid));
                }
            }
            else {
                String[] tokens = line.split("\t");
                if(tokens.length < 6) continue;
                String surface = tokens[5];
                String tag = tokens[0];
                surfaces.add(surface);
                IntPair off = new IntPair(text.length(), text.length() + surface.length());
                offsets.add(off);
                text += surface+" ";
                String fs = "";
                for(int i = 10; i < tokens.length; i++)
                    fs += tokens[i]+"\t";
                fs = fs.trim();
                features.add(fs);

                int xml_start = -1, xml_end = -1;
                if(!tokens[1].equals("x"))
                    xml_start = Integer.parseInt(tokens[1]);
                if(!tokens[2].equals("x"))
                    xml_end = Integer.parseInt(tokens[2]);

                if(tag.contains("-")){
                    String[] tags = tag.split("-");
                    if(tags[0].equals("B")){
                        if(mention != null && mention_start!=-1 && mention_end!=-1){
                            mentions.add(createMention(docid));
                        }
                        mention = surfaces.get(surfaces.size()-1);
                        type = tags[1];
                        mention_start = off.getFirst();
                        mention_end = off.getSecond();
                        mention_xml_start = xml_start;
                        mention_xml_end = xml_end;
                    }
                    else if(tags[0].equals("I") && mention != null){
                        mention += " "+surfaces.get(surfaces.size()-1);
                        mention_end = off.getSecond();
                        mention_xml_end = xml_end;
                    }
                }
                else{
                    if(mention != null && mention_start!=-1 && mention_end!=-1){
                        mentions.add(createMention(docid));
                    }
                }
            }
        }
        if(mention != null && mention_start!=-1 && mention_end!=-1){
            mentions.add(createMention(docid));
        }
        if(sen_ends.size() == 0 || sen_ends.get(sen_ends.size()-1) != surfaces.size())
            sen_ends.add(surfaces.size());
        QueryDocument doc = new QueryDocument(f.getName());
        TextAnnotation ta;
        try {
            ta = createTextAnnotation(text, offsets, surfaces, sen_ends, docid);
        } catch (Exception e) {
            logger.info("bad");
            logger.info(doc.getDocID());
            return null;
        }
        doc.setText(text.trim());
        doc.mentions = mentions;
        doc.setTextAnnotation(ta);
        if (ta.getTokens().length != features.size()) {
            logger.info("# wikifier features doesn't match # tokens!");
            System.exit(-1);
        }
//        System.out.println("3");
        return doc;
    }


    public List<QueryDocument> readDir(String dir, boolean skip_nomen) {
        List<QueryDocument> docs = new ArrayList<>();
        File folder = new File(dir);
        int cnt = 0;
        for(File f: folder.listFiles()){
            System.out.println(cnt++);
            logger.info("Reading "+f.getName());
            QueryDocument doc = readFile(f);
            if(!skip_nomen || doc.mentions.size()>0) {
                docs.add(doc);
            }
            logger.info("Done");
        }
        logger.info("#docs:"+docs.size());
        logger.info("#nes:"+docs.stream().flatMap(x -> x.mentions.stream()).count());
        return docs;
    }

    public static void main(String[] args) {
        ColumnFormatReader r = new ColumnFormatReader();

        String dir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E86_LORELEI_Amharic_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/dryrun-outputs/rpi/Train-stem";
//        String dir = "/shared/corpora/corporaWeb/lorelei/data/LDC2016E86_LORELEI_Amharic_Representative_Language_Pack_Monolingual_Text_V1.1/data/monolingual_text/zipped/final-test2-stem";
        List<QueryDocument> docs = r.readDir(dir, true);

        Set<String> mentions = new HashSet<>();
        for(QueryDocument doc: docs){
            for(ELMention m: doc.mentions)
                mentions.add(m.getSurface().toLowerCase());
        }



        String testdir = "/shared/corpora/ner/lorelei/am/All-nosn-stem";
        List<QueryDocument> test_docs = r.readDir(testdir, true);
        Set<String> tmentions = new HashSet<>();
        for(QueryDocument doc: test_docs){
            for(ELMention m: doc.mentions)
                tmentions.add(m.getSurface().toLowerCase());
        }
        Map<String, List<ELMention>> type2ms = docs.stream().flatMap(x -> x.mentions.stream()).collect(groupingBy(x -> x.getType(), toList()));
        for(String t: type2ms.keySet())
            System.out.println(t+" "+type2ms.get(t).size());

        type2ms = test_docs.stream().flatMap(x -> x.mentions.stream()).collect(groupingBy(x -> x.getType(), toList()));
        for(String t: type2ms.keySet())
            System.out.println(t+" "+type2ms.get(t).size());
        System.out.println(mentions.size()+" "+tmentions.size());

        tmentions.retainAll(mentions);
        System.out.println(tmentions.size());

    }
}
