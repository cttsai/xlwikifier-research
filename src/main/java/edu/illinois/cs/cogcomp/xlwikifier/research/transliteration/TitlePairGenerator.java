package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import com.github.stuxuhai.jpinyin.ChineseHelper;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.tokenizers.ChineseTokenizer;
import edu.illinois.cs.cogcomp.tokenizers.MultiLingualTokenizer;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.LangLinker;
import org.apache.commons.io.FileUtils;
import org.h2.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * This class uses title mapping between English and the target language to
 * generate gazetters for the target language.
 *
 * Created by ctsai12 on 3/9/16.
 */
public class TitlePairGenerator {
    private static final int ntrain = 80000;
    private static final int ntest = 30000;
    private static final int ndev = 30000;
    private static final int np_th = 4;

    private String dir = "/shared/corpora/ner/gazetteers/";

    public static void interSize(){
        try {
            ConfigParameters.setPropValues("config/xlwikifier-tac.config");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(String lang: TransUtils.langs){
            LangLinker ll = LangLinker.getLangLinker(lang);
            System.out.println(lang+" "+ll.to_en.size());
            ll.closeDB();
        }
    }

    public static void genTitlePairs(String lang){

        try {
            ConfigParameters.setPropValues("config/xlwikifier-demo.config");
        } catch (IOException e) {
            e.printStackTrace();
        }

        FreeBaseQuery.loadDB(true);
        LangLinker ll = LangLinker.getLangLinker(lang);
        Set<String> pers = new HashSet<>();
        Set<String> orgs = new HashSet<>();
        Set<String> locs = new HashSet<>();


        ChineseTokenizer ct = new ChineseTokenizer("/shared/experiments/ctsai12/workspace/stanford-segmenter-2015-04-20/data/");

        String query_lang = lang;
        if(lang.equals("zh")) query_lang = "zh-cn";
        for(Object ft_: ll.to_en.keySet()){
            String ft = (String)ft_;
            String mid = FreeBaseQuery.getMidFromTitle(ll.to_en.get(ft), "en");
            List<String> ts = new ArrayList<>();
            if(mid != null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));
            mid = FreeBaseQuery.getMidFromTitle(ft, query_lang);
            if(mid!=null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));


            String netype = "";
            if(ts.contains("people.person")) netype = "PER";
            else if(ts.contains("organization.organization")) netype = "ORG";
            else if(ts.contains("location.location")) netype = "LOC";
            else continue;

            String title = ll.to_en.get(ft).toLowerCase();

            String tt = title.replaceAll("_", " ");
            int idx = tt.indexOf("(");
            if(idx > 0) tt = tt.substring(0, idx);
            tt = tt.trim();
            ft = ft.replaceAll("_", " ");
            idx = ft.indexOf("(");
            if(idx > 0) ft = ft.substring(0, idx);
            ft = ft.trim();

//            if(tt.equals(ft)) continue;
            if(StringUtils.isNumber(tt.substring(0, 1))) continue;
            if(StringUtils.isNumber(ft.substring(0, 1))) continue;

            if(tt.contains("/") || ft.contains("/")) continue;

            tt = tt.replaceAll(",", "").replaceAll("\\s+", " ").toLowerCase();
            ft = ft.replaceAll(",", "").replaceAll("\\s+", " ").toLowerCase();

            if(tt.trim().isEmpty() || ft.trim().isEmpty()) continue;
            if(tt.equals(ft)) continue;

            if(netype.equals("PER")) {
                if(lang.equals("zh")) {
                    ft = ft.replaceAll("·", " ");
                    ft = ChineseHelper.convertToSimplifiedChinese(ft);
                }
                pers.add(ft + "\t" + tt);
            }
            else if(netype.equals("ORG")) {
                if(lang.equals("zh")) {
                    TextAnnotation ta = ct.oldGetTextAnnotation(ft);
                    ft = ta.getTokenizedText();
                    ft = ChineseHelper.convertToSimplifiedChinese(ft);
                }
                orgs.add(ft + "\t" + tt);
            }
            else if(netype.equals("LOC")) {
                if(lang.equals("zh")) {
                    TextAnnotation ta = ct.oldGetTextAnnotation(ft);
                    ft = ta.getTokenizedText();
                    ft = ChineseHelper.convertToSimplifiedChinese(ft);
                }
                locs.add(ft + "\t" + tt);
            }
        }

        System.out.println("#pairs per:"+pers.size()+" org:"+orgs.size()+" loc:"+locs.size());
        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";

        List<String> per = new ArrayList<>(pers);
        Collections.shuffle(per);
        List<String> train = per.subList(0, per.size() / 2);
        List<String> dev = per.subList(per.size()/2, per.size()*7/10);
        List<String> test = per.subList(per.size()*7/10, per.size());

        try {
            FileUtils.writeStringToFile(new File(dir+"per", "train.new"), train.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"per", "dev.new"), dev.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"per", "test.new"), test.stream().collect(joining("\n")), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> loc = new ArrayList<>(locs);
        Collections.shuffle(loc);
        train = loc.subList(0, loc.size() / 2);
        dev = loc.subList(loc.size()/2, loc.size()*7/10);
        test = loc.subList(loc.size()*7/10, loc.size());

        try {
            FileUtils.writeStringToFile(new File(dir+"loc", "train.new"), train.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"loc", "dev.new"), dev.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"loc", "test.new"), test.stream().collect(joining("\n")), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> org = new ArrayList<>(orgs);
        Collections.shuffle(org);
        train = org.subList(0, org.size() / 2);
        dev = org.subList(org.size()/2, org.size()*7/10);
        test = org.subList(org.size()*7/10, org.size());

        try {
            FileUtils.writeStringToFile(new File(dir+"org", "train.new"), train.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"org", "dev.new"), dev.stream().collect(joining("\n")), "UTF-8");
            FileUtils.writeStringToFile(new File(dir+"org", "test.new"), test.stream().collect(joining("\n")), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void printNaiveAlignedPairs(String trainfile, String outdir, int npair, String name){

        for(int i = 4; i > 0; i--) {
            List<Pair<String[], String[]>> part_pairs = TransUtils.readPairs(trainfile, npair, i);
            List<Pair<String, String>> pairs = TransUtils.alignWords(part_pairs);

            String out = pairs.stream().map(x -> x.getFirst() + "\t" + x.getSecond()).collect(joining("\n"));

            try {
                FileUtils.writeStringToFile(new File(outdir, name+"."+String.valueOf(i)), out, "UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public static void makeData(String lang){
        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";

        List<String> types = Arrays.asList("loc", "org", "per");

        for(String type: types) {
            String infile = dir + type + "/train.new";
            String testfile = dir + type + "/test.new";
            String devfile = dir + type + "/dev.new";
//            printNaiveAlignedPairs(infile, dir + type + "/naive-align/", ntrain, "train");
//            printNaiveAlignedPairs(devfile, dir + type + "/naive-align/", ndev, "dev");
//            printTestTokens(testfile, dir + type + "/test.tokens");
            printSelectedPairs(infile, testfile, devfile, dir + type);
        }
    }

    public static void printSelectedPairs(String infile, String testfile, String devfile, String outdir) {
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile, ntest, np_th);
        List<Pair<String[], String[]>> train_pairs = TransUtils.readPairs(infile, ntrain, np_th);
        List<Pair<String[], String[]>> dev_pairs = TransUtils.readPairs(devfile, ndev, np_th);

        String d = " ";
//        if(outdir.contains("/zh"))
//            d = "·";

        String out = "";
        for(Pair<String[], String[]> pair: train_pairs){
            String s1 = Arrays.stream(pair.getFirst()).collect(joining(d));
            String s2 = Arrays.stream(pair.getSecond()).collect(joining(" "));
            out+=s1+"\t"+s2+"\n";
        }
        try {
            FileUtils.writeStringToFile(new File(outdir, "train.new.more2"), out, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        out = "";
        for(Pair<String[], String[]> pair: dev_pairs){
            String s1 = Arrays.stream(pair.getFirst()).collect(joining(d));
            String s2 = Arrays.stream(pair.getSecond()).collect(joining(" "));
            out+=s1+"\t"+s2+"\n";
        }
        try {
            FileUtils.writeStringToFile(new File(outdir, "dev.new.more2"), out, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        out = "";
        for(Pair<String[], String[]> pair: test_pairs){
            String s1 = Arrays.stream(pair.getFirst()).collect(joining(d));
            String s2 = Arrays.stream(pair.getSecond()).collect(joining(" "));
            out+=s1+"\t"+s2+"\n";
        }
        try {
            FileUtils.writeStringToFile(new File(outdir, "test.new.more2"), out, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void printTestTokens(String testfile, String outfile){
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile, ntest, np_th);

        Set<String> tokenset = test_pairs.stream().flatMap(x -> Arrays.asList(x.getFirst()).stream())
                .collect(Collectors.toSet());

        String out = tokenset.stream().collect(joining("\n"));
        try {
            FileUtils.writeStringToFile(new File(outfile), out, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void printPairsForFastAlign(String trainfile, String outdir){

        List<Pair<String[], String[]>> part_pairs = TransUtils.readPairs(trainfile, ntrain, np_th);

        String out = "";
        for(Pair<String[], String[]> pair: part_pairs){

            String src = Arrays.asList(pair.getFirst()).stream().collect(joining(" "));
            String tgt = Arrays.asList(pair.getSecond()).stream().collect(joining(" "));

            out+= src+" ||| "+tgt+"\n";
        }

        try {
            FileUtils.writeStringToFile(new File(outdir, "fa"), out, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printJanusData(String infile, String testfile, String lang, String type){
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);

        String out = "", out1 = "";
        for(Pair<String[], String[]> pair: test_pairs){
            out += spaceSplittedPhrase(pair.getFirst())+"\n";
            out1 += spaceSplittedPhrase(pair.getSecond())+"\n";
        }

        try {
            FileUtils.writeStringToFile(new File("/shared/corpora/ner/gazetteers/"+lang+"/janus/"+type+".test.src"), out, "UTF-8");
            FileUtils.writeStringToFile(new File("/shared/corpora/ner/gazetteers/"+lang+"/janus/"+type+".test.tgt"), out1, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Pair<String[], String[]>> part_pairs = TransUtils.readPairs(infile, ntrain, np_th);
        out = "";
        out1 = "";
        for(Pair<String[], String[]> pair: part_pairs){
            out += spaceSplittedPhrase(pair.getFirst())+"\n";
            out1 += spaceSplittedPhrase(pair.getSecond())+"\n";
        }

        try {
            FileUtils.writeStringToFile(new File("/shared/corpora/ner/gazetteers/"+lang+"/janus/"+type+".train.src"), out, "UTF-8");
            FileUtils.writeStringToFile(new File("/shared/corpora/ner/gazetteers/"+lang+"/janus/"+type+".train.tgt"), out1, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * For JANUS
     * @param word
     * @return
     */
    public String spaceSplittedWord(String word){
        String tmp = "";
        for(int i = 0; i < word.length(); i++)
            tmp += word.charAt(i)+" ";
        return tmp.trim();
    }

    public String spaceSplittedPhrase(String[] words){
        String tmp = "";
        for(String word: words){
            tmp += spaceSplittedWord(word);
            tmp += "   ";
        }
        return tmp.trim();
    }

    /**
     * Transform the tab seperated word pairs to the Sequitur format
     * @param lang
     */
    public static void toSequiturData(String lang){
        List<String> types = Arrays.asList("loc", "org", "per");


        for(String type: types){
//            String dir = "/shared/corpora/ner/transliteration/"+lang+"/"+type+"/fast-align";
            String dir = "/shared/corpora/ner/transliteration/"+lang+"/"+type+"/naive-align";
            try {
                String out = "";
                for(String line: LineIO.read(dir+"/train.4")){

                    String[] parts = line.split("\t");
                    out += parts[0]+"\t";
                    for(int i = 0; i < parts[1].length(); i++)
                        out += parts[1].charAt(i)+" ";
                    out = out.trim()+"\n";
                }

                FileUtils.writeStringToFile(new File(dir+"/train.seq"), out, "UTF-8");

                out = "";
                for(String line: LineIO.read(dir+"/dev.4")){

                    String[] parts = line.split("\t");
                    out += parts[0]+"\t";
                    for(int i = 0; i < parts[1].length(); i++)
                        out += parts[1].charAt(i)+" ";
                    out = out.trim()+"\n";
                }
                FileUtils.writeStringToFile(new File(dir+"/dev.seq"), out, "UTF-8");

            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void toDirecTLData(String lang) {
        List<String> types = Arrays.asList("loc", "org", "per");

        String dir = "/shared/corpora/ner/transliteration/" + lang + "/";

        for (String type : types) {
            try {
                String out = "";
                for (String line : LineIO.read(dir + type + "/naive-align/train.4")) {
//                for (String line : LineIO.read(dir + type + "/fast-align/train")) {

                    String[] parts = line.split("\t");
                    for (int i = 0; i < parts[0].length(); i++)
                        out += parts[0].charAt(i) + " ";
                    out = out.trim() + "\t";
                    for (int i = 0; i < parts[1].length(); i++)
                        out += parts[1].charAt(i) + " ";
                    out = out.trim() + "\n";
                }

                FileUtils.writeStringToFile(new File(dir + type + "/naive-align/train.4.dir"), out, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir + type + "/fast-align/train.dir"), out, "UTF-8");

                out = "";
                for (String line : LineIO.read(dir + type + "/naive-align/dev.4")) {
//                for (String line : LineIO.read(dir + type + "/fast-align/dev")) {

                    String[] parts = line.split("\t");
                    for (int i = 0; i < parts[0].length(); i++)
                        out += parts[0].charAt(i) + " ";
                    out = out.trim() + "\t";
                    for (int i = 0; i < parts[1].length(); i++)
                        out += parts[1].charAt(i) + " ";
                    out = out.trim() + "\n";
                }

                FileUtils.writeStringToFile(new File(dir + type + "/naive-align/dev.4.dir"), out, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir + type + "/fast-align/dev.dir"), out, "UTF-8");

                out = "";
                for (String line : LineIO.read(dir + type + "/test.tokens")) {

                    for (int i = 0; i < line.length(); i++) {
                        out += line.charAt(i);
                        if (i < line.length() - 1)
                            out += "|";
                    }
                    out = out.trim() + "\n";
                }
                FileUtils.writeStringToFile(new File(dir + type + "/test.tokens.dir"), out, "UTF-8");
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void toJanus(String lang){

        List<String> types = Arrays.asList("loc", "org", "per");

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";

        for(String type: types){
            try {
                String out1 = "", out2="";
                for(String line: LineIO.read(dir+type+"/naive-align/train.4")){
//                for(String line: LineIO.read(dir+type+"/fast-align/train")){
                    String[] parts = line.split("\t");
                    for(int i = 0; i < parts[0].length(); i++)
                        out1 += parts[0].charAt(i)+" ";
                    out1 = out1.trim()+"\n";
                    for(int i = 0; i < parts[1].length(); i++)
                        out2 += parts[1].charAt(i)+" ";
                    out2 = out2.trim()+"\n";
                }

                FileUtils.writeStringToFile(new File(dir+type+"/naive-align/janus/train."+lang), out1, "UTF-8");
                FileUtils.writeStringToFile(new File(dir+type+"/naive-align/janus/train.en"), out2, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir+type+"/fast-align/janus/train."+lang), out1, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir+type+"/fast-align/janus/train.en"), out2, "UTF-8");

                out1 = "";
                out2="";
                for(String line: LineIO.read(dir+type+"/naive-align/dev.4")){
//                for(String line: LineIO.read(dir+type+"/fast-align/dev")){

                    String[] parts = line.split("\t");
                    for(int i = 0; i < parts[0].length(); i++)
                        out1 += parts[0].charAt(i)+" ";
                    out1 = out1.trim()+"\n";
                    for(int i = 0; i < parts[1].length(); i++)
                        out2 += parts[1].charAt(i)+" ";
                    out2 = out2.trim()+"\n";
                }

                FileUtils.writeStringToFile(new File(dir+type+"/naive-align/janus/dev."+lang), out1, "UTF-8");
                FileUtils.writeStringToFile(new File(dir+type+"/naive-align/janus/dev.en"), out2, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir+type+"/fast-align/janus/dev."+lang), out1, "UTF-8");
//                FileUtils.writeStringToFile(new File(dir+type+"/fast-align/janus/dev.en"), out2, "UTF-8");

                out1 = "";
                out2="";
                for(String line: LineIO.read(dir+type+"/test.tokens")){
                    String[] parts = line.split("\t");
                    for(int i = 0; i < parts[0].length(); i++)
                        out1 += parts[0].charAt(i)+" ";
                    out1 = out1.trim()+"\n";
                }

                FileUtils.writeStringToFile(new File(dir+type+"/janus/test.tokens."+lang), out1, "UTF-8");
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

//        List<String> langs = Arrays.asList("zh");
//        List<String> langs = Arrays.asList("es","de","tr","tl","bn","fr", "it", "he", "ar");
        List<String> langs = Arrays.asList("zh");
///        String lang = args[0];
//        interSize();
//        System.exit(-1);

        for(String lang: langs) {
//			if(lang.equals("zh"))
//				TransUtils.del = "·";
            // generate wiki title pairs
//            genTitlePairs(lang);

            // make train, dev, and test splits, as well as naive word alignment baseline
            makeData(lang);

//            toSequiturData(lang);
//            toDirecTLData(lang);
//            toJanus(lang);
        }
    }
}
