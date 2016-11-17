package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.io.LineIO;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ctsai12 on 10/6/16.
 */
public class LanguageModel {

    public Map<String, Double> four2prob;
    public Map<String, Double> three2prob;
    public Map<String, Double> two2prob;

    private Map<String, Map<String, Double>>  tri;
    private Map<String, Map<String, Double>>  bi;
    private Map<String, Map<String, Double>>  uni;

    public LanguageModel(){
        loadModel();
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
        lm.generateLM();
        String outdir = "/shared/preprocessed/ctsai12/multilingual/xlwikifier-data/lm";
        lm.writeModel(outdir);

    }

}
