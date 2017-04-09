package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;
import java.io.*;
import java.util.*;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.transliteration.SPModel;


public class MentionPredictor{

    public static List<String> types = Arrays.asList("loc", "org", "per");

	public static void runJeffModel(String lang){

		TitleTranslator.lang = lang;
		Map<String, JointModel> models  = new HashMap<>();

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";
		String outfile = dir+"canddata1.jeff.falign.new";
		String origfile = dir+"/canddata1";

		for(String type: types){
			JointModel tmp = new JointModel();
			try{
				tmp.spmodel = new SPModel(dir+type+"/models/jeff.falign.new.best");
			}catch(IOException e){
				e.printStackTrace();
			}
			models.put(type, tmp);
		}
		try{
			BufferedReader br = new BufferedReader(new FileReader(origfile));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
			String line = null;
			int cnt = 0;
			while((line = br.readLine()) != null){
				if((cnt++)%100 == 0) System.out.print(cnt+"\r");
				String[] parts = line.trim().split("\t");
				String mention = parts[0].toLowerCase();
				String type = parts[1].toLowerCase();

				String[] words = mention.split("\\s+");
				if(!models.containsKey(type))
					System.out.println(type);
				List<String> preds = models.get(type).generatePhrase(words);

				String outstr = "";
				if(preds != null)
					for(String p: preds)
						outstr += p+"\t";
				bw.write(outstr.trim()+"\n");
			}
			br.close();
			bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
		//CandidateEvaluator.evaluate(origfile, outfile, lang);

	}

	public static Map<String, Map<String, String>> loadJointModelPath(){

		Map<String, Map<String, String>> ret = new HashMap<>();

		String file = "/shared/corpora/ner/transliteration/joint.model.list";
//		String file = "/shared/corpora/ner/transliteration/joint.model.list.cand";

		String model_pre = "/shared/corpora/ner/transliteration/";
		try {
			for(String line: LineIO.read(file)){
				String[] parts = line.split("\t");
				if(parts.length < 3) continue;
				if(!ret.containsKey(parts[0]))
					ret.put(parts[0], new HashMap<>());
				ret.get(parts[0]).put(parts[1], model_pre+parts[0]+"/"+parts[1]+"/models/"+parts[2]);
//				ret.get(parts[0]).put(parts[1], model_pre+parts[0]+"/"+parts[1]+"/models/best6-iter9");
            }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return ret;
	}

	public static void runJointModel(String lang){

		Map<String, Map<String, String>> model_paths = loadJointModelPath();

		TitleTranslator.lang = lang;
		Map<String, JointModel> models  = new HashMap<>();

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";
		String outfile = dir+"canddata1.joint.gogo2";
		String origfile = dir+"/canddata1";

		for(String type: types){
			models.put(type, JointModel.loadModel(model_paths.get(lang).get(type)));
		}
		try{
			BufferedReader br = new BufferedReader(new FileReader(origfile));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
			String line = null;
			int cnt = 0;
			while((line = br.readLine()) != null){
				if((cnt++)%100 == 0) System.out.print(cnt+"\r");
				String[] parts = line.trim().split("\t");
				String mention = parts[0].toLowerCase();
				String type = parts[1].toLowerCase();

				String[] words = mention.split("\\s+");
				List<String> preds = models.get(type).generatePhraseAlign(words);
				if(preds == null)
					//|| preds.size()==0 || preds.get(0).trim().isEmpty())
					preds = models.get(type).generatePhrase(words);

				String outstr = "";
				if(preds != null)
					for(String p: preds)
						outstr += p+"\t";
				bw.write(outstr.trim()+"\n");
			}
			br.close();
			bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
	//	CandidateEvaluator.evaluate(origfile, outfile, lang);
	}


	public static void main(String[] args){
        ConfigParameters.setPropValues();

//		String lang = args[0];
		List<String> langs = Arrays.asList("de");

		for(String lang: langs) {
//			for(String lang: langs) {
			System.out.println("======= "+lang+" ========");
//			runJeffModel(lang);
			runJointModel(lang);
		}

	}

}
