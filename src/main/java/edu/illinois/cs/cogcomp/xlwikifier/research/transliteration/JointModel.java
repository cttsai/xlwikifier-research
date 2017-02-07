package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.transliteration.SPModel;
import java.util.*;
import java.io.*;
import org.apache.commons.io.FileUtils;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import java.util.stream.Collectors;


public class JointModel{

    public Map<String,Map<String, Double>> s2t2prob;

    public Map<String, Map<String, Double>> e2f2prob;
    public Map<String, Map<String, Double>> m2a2prob;
    public Map<String, Map<String, Double>> en2fo2prob;

    public Map<String, Map<String, Double>> m2a2c;
    public Map<String, Double> m2c;
    public Map<String, Double> e2c;
    public Map<String, Map<String, Double>> e2f2c;

    public Map<String, String> word_align;
    public Map<String, String> phrase_align;
    public Map<String, Double> memorization;
    public SPModel spmodel;
    public LanguageModel lm;
    public boolean all_length = false;
    public boolean use_lm = true;

	public JointModel(){
		m2a2prob = new HashMap<>();
		e2f2prob = new HashMap<>();
		s2t2prob = new HashMap<>();
		m2a2c = new HashMap<>();
		m2c = new HashMap<>();
		en2fo2prob = new HashMap<>();
		e2f2c = new HashMap<>();
		e2c = new HashMap<>();
		word_align = new HashMap<>();
		phrase_align = new HashMap<>();

		lm = new LanguageModel();
		lm.loadDB(true);
	}

    public void saveModel(String path){
        System.out.println("Writing probs...");

        try {
            FileUtils.forceMkdir(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(path+"/gen.prob"));
            for(String s: s2t2prob.keySet()){
                for(String t: s2t2prob.get(s).keySet()){
                    if(!s.trim().isEmpty() && !t.trim().isEmpty()) {
                        Double val = s2t2prob.get(s).get(t);
                        if(val>0.0000000001)
                            bw.write(s + "\t" + t + "\t" + val + "\n");
                    }
                }
            }
            bw.close();

            bw = new BufferedWriter(new FileWriter(path+"/align.prob"));
            for(String m: m2a2prob.keySet()){
                for(String a: m2a2prob.get(m).keySet()){
                    if(!m.trim().isEmpty() && !a.trim().isEmpty()) {
                        Double val = m2a2prob.get(m).get(a);
                        if(val>0.0000000001)
                            bw.write(m + "\t" + a + "\t" + val + "\n");
                    }
                }
            }
            bw.close();

            bw = new BufferedWriter(new FileWriter(path+"/phrase.align"));
            for(String p: phrase_align.keySet()){
				bw.write(p + "\t" + phrase_align.get(p) + "\n");
            }
            bw.close();

            bw = new BufferedWriter(new FileWriter(path+"/word.align"));
            for(String p: word_align.keySet()){
				bw.write(p + "\t" + word_align.get(p) + "\n");
            }
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JointModel loadModel(String dir){

        String[] dirparts = dir.trim().split("\\s+");
        dir = dirparts[0];

		JointModel model = new JointModel();

		try{
//			model.spmodel = new SPModel(dir+"/gen.prob");
			BufferedReader br = new BufferedReader(new FileReader(dir+"/align.prob"));
			String line = null;
			while((line = br.readLine()) != null){
				String[] parts = line.trim().split("\t");
				if(!model.m2a2prob.containsKey(parts[0]))
					model.m2a2prob.put(parts[0], new HashMap<>());
				model.m2a2prob.get(parts[0]).put(parts[1], Double.parseDouble(parts[2]));
			}
			br.close();

			br = new BufferedReader(new FileReader(dir+"/gen.prob"));
			line = null;
			while((line = br.readLine()) != null){
				String[] parts = line.trim().split("\t");
				if(!model.s2t2prob.containsKey(parts[0]))
					model.s2t2prob.put(parts[0], new HashMap<>());
				model.s2t2prob.get(parts[0]).put(parts[1], Double.parseDouble(parts[2]));
			}
			br.close();
			model.spmodel = new SPModel(model.s2t2prob);

			br = new BufferedReader(new FileReader(dir+"/word.align"));
			line = null;
			while((line = br.readLine()) != null){
				String[] parts = line.trim().split("\t");
				model.word_align.put(parts[0], parts[1]);
			}
			br.close();

			br = new BufferedReader(new FileReader(dir+"/phrase.align"));
			line = null;
			while((line = br.readLine()) != null){
				String[] parts = line.trim().split("\t");
				model.phrase_align.put(parts[0], parts[1]);
			}
			br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

		model.memorization = new HashMap<>();

		if(dir.contains("all") && dirparts.length > 1 && dirparts[1].equals("real")) {
            System.out.println(dir);
            model.all_length = true;
        }

		return model;
    }

    public List<String> generatePhraseAlign(String[] parts){

		/*
		if(spmodel == null){
			spmodel = new SPModel(s2t2prob);
		}
		*/

        spmodel.setMaxCandidates(5);

        List<String> sources = new ArrayList<>();
        List<String> targets = new ArrayList<>();

        String phrase = Arrays.asList(parts).stream().collect(joining(" "));
        if(phrase_align.containsKey(phrase)){
            List<String> ret = new ArrayList<>();
            ret.add(phrase_align.get(phrase));
            return ret;
        }


        for(String part: parts){
            List<Pair<Double, String>> prediction = new ArrayList<>();
            try {

				if(word_align.containsKey(part))
					prediction.add(new Pair<>(1.0, word_align.get(part)));
				else
					prediction = spmodel.Generate(part).toList();

            } catch (Exception e) {
                e.printStackTrace();
            }

            sources.add(part);
            if(prediction.size() > 0)
                targets.add(prediction.get(0).getSecond());
        }

        int n = sources.size();
		List<List<String>> target_cands = new ArrayList<>();
//		if(targets.size() < 5) {
            for (int i = targets.size(); i > 0 && (this.all_length || TransUtils.all_length || i > targets.size() - 1); i--) {
                target_cands.addAll(perm(targets, i));
            }
//        }
//        else
//            target_cands.add(targets);


        String m = String.valueOf(parts.length);

        Map<String, Double> results = new HashMap<>();

        // cand phrase
        for(List<String> cand: target_cands){

            double sum = 0;
			if(!m2a2prob.containsKey(m)){
				return null;
			}
            for(String a: m2a2prob.get(m).keySet()){

                List<Integer> align = Arrays.asList(a.split("_")).stream()
                        .map(x -> Integer.parseInt(x)).collect(Collectors.toList());

				if(cand.size() != align.size()) continue;

                double score = m2a2prob.get(m).get(a);
//                System.out.println(score);
                for(int i = 0; i < align.size(); i++) {
					double s = getProdProb(sources.get(align.get(i)), cand.get(i));
					score *= s;
                }
                sum += score;
            }

            if(!TransUtils.all_length && !all_length) {
                double prob = lm.getPhraseProb(cand);
                if(cand.equals(targets))
                    prob += 1e-9;
                sum *= prob;
            }

            results.put(cand.stream().filter(x -> !x.isEmpty()).collect(joining(" ")), sum);
        }
//        System.out.println(results);

        List<String> sorted = results.entrySet().stream()
                .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                .map(x -> x.getKey())
                .collect(Collectors.toList());

        return sorted;
    }

    public double getProdProb(String source, String target){

        if(source.isEmpty() && target.isEmpty())
            return 1;

        if(source.isEmpty() || target.isEmpty())
            return 0;

        String key = source+"|"+target;
        if(memorization.containsKey(key))
            return memorization.get(key);


        double probsum = 0;
        for(int i = 1; i <= source.length(); i++){
            String src_head = source.substring(0, i);
            String src_tail = source.substring(i, source.length());
            for(int j = 1; j <=target.length(); j++){
                String tgt_head = target.substring(0, j);
                String tgt_tail = target.substring(j, target.length());

                double prob = getProdProb(src_tail, tgt_tail);
//                if(prob == 0) continue;

                if(!s2t2prob.containsKey(src_head) || !s2t2prob.get(src_head).containsKey(tgt_head))
                    continue;
//                    return 0;
                probsum += s2t2prob.get(src_head).get(tgt_head)*prob;
            }
        }

        memorization.put(key, probsum);
        return probsum;

    }
    public List<List<String>> perm(List<String> input, int level){

        List<List<String>> results = new ArrayList<>();

		if(level == 0){
			results.add(new ArrayList());
			return results;
		}

        for(int i = 0; i < input.size(); i++){

           	String a = input.get(i);

			List<String> rest = new ArrayList();
			for(int j = 0; j < input.size(); j++){
				if(j!=i)
					rest.add(input.get(j));
			}

            List<List<String>> perms = perm(rest, level-1);

            for(List<String> p: perms)
                p.add(a);

            results.addAll(perms);
        }

        return results;
    }

    public List<String> generatePhrase(String[] parts){
		/*
		if(spmodel == null){
			spmodel = new SPModel(s2t2prob);
		}
		*/
        spmodel.setMaxCandidates(3);

        List<String> preds = new ArrayList<>();
        List<List<String>> predsl = new ArrayList<>();

        String phrase = Arrays.asList(parts).stream().collect(joining(" "));
        if(phrase_align.containsKey(phrase)){
            List<String> ret = new ArrayList<>();
            ret.add(phrase_align.get(phrase));
            return ret;
        }

        for(String part: parts) {
            try {
                List<Pair<Double, String>> prediction = new ArrayList<>();
                if(word_align.containsKey(part))
                    prediction.add(new Pair<>(1.0, word_align.get(part)));
                prediction.addAll(spmodel.Generate(part).toList());


                for(int i = 0; i < prediction.size(); i++){
                    if(predsl.size() > i)
                        predsl.get(i).add(prediction.get(i).getSecond());
                    else {
                        List<String> tmp = new ArrayList<>();
                        tmp.add(prediction.get(i).getSecond());
                        predsl.add(tmp);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        predsl = predsl.stream().filter(x -> x.size() == parts.length).collect(toList());

        if(use_lm) {
            Map<List<String>, Double> phrase2score = new HashMap<>();
            for (List<String> pred : predsl) {

                for (List<String> p : TransUtils.perm(pred, pred.size())) {
                    double prob = lm.getPhraseProb(p);
                    if (p.equals(pred))
                        prob += 1e-9;
                    phrase2score.put(p, prob);
                }
                break;
            }

            predsl = phrase2score.entrySet().stream().sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                    .map(x -> x.getKey()).collect(toList());

        }

        preds = predsl.stream().map(x -> x.stream().collect(joining(" "))).collect(toList());

        return preds;
    }
}
