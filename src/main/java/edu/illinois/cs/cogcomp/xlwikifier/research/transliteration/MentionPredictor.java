package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;
import java.io.*;
import java.util.*;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.transliteration.SPModel;


public class MentionPredictor{

    public static List<String> types = Arrays.asList("loc", "org", "per");

	public static void runJeffModel(String lang){

		TitleTranslator.lang = lang;
		Map<String, JointModel> models  = new HashMap<>();

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";
		String outfile = dir+"canddata1.jeff.palign";
		String origfile = dir+"/canddata1";

		for(String type: types){
			JointModel tmp = new JointModel();
			try{
				tmp.spmodel = new SPModel(dir+type+"/models/jeff.palign");
			}catch(IOException e){}
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

	public static void runJointModel(String lang){

		TitleTranslator.lang = lang;
		Map<String, JointModel> models  = new HashMap<>();

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";
		String outfile = dir+"canddata1.joint.best5";
		String origfile = dir+"/canddata1";

		for(String type: types){
			models.put(type, JointModel.loadModel(dir+type+"/models/best5"));
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

		String lang = args[0];

//		runJointModel(lang);
		runJeffModel(lang);

	}

}
