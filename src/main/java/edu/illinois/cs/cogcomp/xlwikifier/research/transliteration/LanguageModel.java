package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.core.StopWord;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArray;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ctsai12 on 10/6/16.
 */
public class LanguageModel {

    public Map<String, Double> four2prob;
    public Map<String, Double> three2prob;
    public Map<String, Double> two2prob;
    private DB db;
    public BTreeMap<String, Double> biprob;

    private Map<String, Map<String, Double>>  tri;
    private Map<String, Map<String, Double>>  bi;
    private Map<String, Map<String, Double>>  uni;

    public LanguageModel(){
        loadModel();
    }

    public void loadDB(boolean read_only) {
        String dbfile = "/shared/corpora/ner/transliteration/lm/mapdb1";
        if (read_only) {
            db = DBMaker.fileDB(new File(dbfile))
                    .fileChannelEnable()
                    .allocateStartSize(1024*1024*1024)
                    .allocateIncrement(512*1024*1024)
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
            biprob = db.treeMap("bi", Serializer.STRING, Serializer.DOUBLE)
                    .open();
        } else {
            db = DBMaker.fileDB(new File(dbfile))
                    .closeOnJvmShutdown()
                    .make();
            biprob = db.treeMap("bi", Serializer.STRING, Serializer.DOUBLE)
                    .create();
        }
    }

    public void generateNgramCount(){

        loadDB(false);

        Set<String> stops = StopWord.getStopWords("en");

        bi = new HashMap<>();
        tri = new HashMap<>();

        String file = "/shared/bronte/ctsai12/multilingual/text/en.notitle";
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = null;
            int cnt = 0;
            while((line = br.readLine())!= null){
                if(cnt++%10000 == 0) System.out.print("Read "+cnt+" lines\r");

                line = line.toLowerCase().trim();

                String[] tokens = line.trim().split("\\s+");

                for(int i = 0; i < tokens.length; i++){
                    if(i > 0){
                        String prev = tokens[i-1];
                        TransUtils.addToMap(prev, tokens[i], 1.0, bi);
                    }
//                    if(i > 1){
//                        String prev = tokens[i-2]+" "+tokens[i-1];
//                        TransUtils.addToMap(prev, tokens[i], 1.0, tri);
//                    }
                }
            }
            br.close();


            TransUtils.normalizeProb(tri);
            TransUtils.normalizeProb(bi);

            String outdir = "/shared/corpora/ner/transliteration/lm/";

//            BufferedWriter bw = new BufferedWriter(new FileWriter(outdir + "tri"));
//            for(String key1: tri.keySet()){
//                for(String key2: tri.get(key1).keySet())
//                    bw.write(key1+"\t"+key2+"\t"+tri.get(key1).get(key2)+"\n");
//            }
//            bw.close();

//            BufferedWriter bw = new BufferedWriter(new FileWriter(outdir + "bi"));
            System.out.println(bi.size());
            cnt = 0;
            for(String key1: bi.keySet()){
                if(cnt++%10000 == 0) System.out.print(cnt+"\r");
                for(String key2: bi.get(key1).keySet()) {
                    Double val = bi.get(key1).get(key2);
                    if(val > 1e-6 && !stops.contains(key2) && !stops.contains(key1))
//                        bw.write(key1 + "\t" + key2 + "\t" + val + "\n");
                        biprob.put(key1+" ||| "+key2, val);
                }
            }
            db.commit();
            db.close();
//            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void generateLM(){

        tri = new HashMap<>();
        bi = new HashMap<>();
        uni = new HashMap<>();

        String file = "/shared/corpora/ner/gazetteers/en/all";
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = null;
            while((line = br.readLine())!= null){

                line = line.toLowerCase();

                String[] tokens = line.trim().split("\\s+");
                for(String token: tokens){
                    for(int i = 3; i < token.length(); i++){
                        TransUtils.addToMap(token.substring(i-3, i), token.substring(i, i+1), 1.0, tri);
                    }

                    for(int i = 2; i < token.length(); i++){
                        TransUtils.addToMap(token.substring(i-2, i), token.substring(i, i+1), 1.0, bi);
                    }
                    for(int i = 1; i < token.length(); i++){
                        TransUtils.addToMap(token.substring(i-1, i), token.substring(i, i+1), 1.0, uni);
                    }
                }

            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        TransUtils.normalizeProb(tri);
        TransUtils.normalizeProb(bi);
        TransUtils.normalizeProb(uni);
    }

    private void writeModel(String dir){

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dir, "tri")));
            for(String k: tri.keySet()){
                for(String c: tri.get(k).keySet())
                    bw.write(k+c+"\t"+tri.get(k).get(c)+"\n");
            }
            bw.close();

            bw = new BufferedWriter(new FileWriter(new File(dir, "bi")));
            for(String k: bi.keySet()){
                for(String c: bi.get(k).keySet())
                    bw.write(k+c+"\t"+bi.get(k).get(c)+"\n");
            }
            bw.close();

            bw = new BufferedWriter(new FileWriter(new File(dir, "uni")));
            for(String k: uni.keySet()){
                for(String c: uni.get(k).keySet())
                    bw.write(k+c+"\t"+uni.get(k).get(c)+"\n");
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getPhraseProb(List<String> words){

        double score = 1;

        for(int i = 1; i < words.size(); i++){
            String prev = words.get(i-1);
            double prob = 1e-9;
            String key = words.get(i-1)+" ||| "+words.get(i);
            if(biprob.containsKey(key))
                prob = biprob.get(key);
            score *= prob;
        }
        return score;
    }

    public double getWordProb(String word){

        double ret = 1.0;

        if(word.length() >= 4) {
            for (int i = 4; i < word.length(); i++) {
                String fourgram = word.substring(i - 4, i);
                if (four2prob.containsKey(fourgram))
                    ret *= four2prob.get(fourgram);
                else {
                    String trigram = word.substring(i - 4, i - 1);
                    String trigram1 = word.substring(i - 3, i);
                    if (three2prob.containsKey(trigram) && three2prob.containsKey(trigram1))
                        ret *= three2prob.get(trigram) * three2prob.get(trigram1);
                    else {
                        String bigram = word.substring(i - 4, i - 2);
                        String bigram1 = word.substring(i - 3, i - 1);
                        String bigram2 = word.substring(i - 2, i);
                        if (two2prob.containsKey(bigram) && two2prob.containsKey(bigram1) && two2prob.containsKey(bigram2))
                            ret *= two2prob.get(bigram) * two2prob.get(bigram1) * two2prob.get(bigram2);
                        else
                            ret = 0.000000000000000000001;
                    }
                }
            }
        }
        else if(word.length() == 3){
            if (three2prob.containsKey(word))
                ret *= three2prob.get(word);
            else {
                String bigram = word.substring(0, 2);
                String bigram1 = word.substring(1, 3);
                if (two2prob.containsKey(bigram) && two2prob.containsKey(bigram1))
                    ret *= two2prob.get(bigram) * two2prob.get(bigram1);
                else
                    ret = 0.000000000000000000001;
            }
        }
        else if(word.length() == 2){
            if(two2prob.containsKey(word))
                ret *= two2prob.get(word);
            else
                ret = 0.000000000000000000001;
        }
        else
            ret = 0.000000000000000000001;

        return ret;
    }

    public void loadModel1(){

        String dir = "/shared/corpora/ner/transliteration/lm/";
        bi = new HashMap<>();
        tri = new HashMap<>();
        System.out.println("Loading language model");

        try {
//            for(String line: LineIO.read(dir+"/tri")){
//                String[] parts = line.trim().split("\t");
//                if(parts.length < 3) continue;
//                if(!tri.containsKey(parts[0]))
//                    tri.put(parts[0], new HashMap<>());
//                Map<String, Double> submap = tri.get(parts[0]);
//                submap.put(parts[1], Double.parseDouble(parts[2]));
//            }

            for(String line: LineIO.read(dir+"/bi")){
                String[] parts = line.trim().split("\t");
                if(parts.length < 3) continue;
                if(!bi.containsKey(parts[0]))
                    bi.put(parts[0], new HashMap<>());
                Map<String, Double> submap = bi.get(parts[0]);
                submap.put(parts[1], Double.parseDouble(parts[2]));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("Done");
    }

    public void loadModel(){

        String dir = "/shared/preprocessed/ctsai12/multilingual/xlwikifier-data/lm";
        four2prob = new HashMap<>();
        three2prob = new HashMap<>();
        two2prob = new HashMap<>();

        try {
            for(String line: LineIO.read(dir+"/tri")){
                String[] parts = line.trim().split("\t");
                if(parts.length < 2) continue;
                four2prob.put(parts[0], Double.parseDouble(parts[1]));
            }

            for(String line: LineIO.read(dir+"/bi")){
                String[] parts = line.trim().split("\t");
                if(parts.length < 2) continue;
                three2prob.put(parts[0], Double.parseDouble(parts[1]));
            }

            for(String line: LineIO.read(dir+"/uni")){
                String[] parts = line.trim().split("\t");
                if(parts.length < 2) continue;
                two2prob.put(parts[0], Double.parseDouble(parts[1]));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        LanguageModel lm = new LanguageModel();
        lm.generateNgramCount();
//        lm.generateLM();
//        String outdir = "/shared/preprocessed/ctsai12/multilingual/xlwikifier-data/lm";
//        lm.writeModel(outdir);

    }

}
