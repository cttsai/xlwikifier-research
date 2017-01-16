package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.apache.commons.io.FileUtils;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.tokenizers.MultiLingualTokenizer;



import java.io.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;

import edu.illinois.cs.cogcomp.xlwikifier.core.WikiCandidateGenerator;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.*;



public class CandidateEvaluator{

    private static List<String> types = Arrays.asList("loc", "org", "per");
	public static void genSequiturPrediction(String origfile, String lang){

		Map<String, Map<String, List<String>>> t2preds = new HashMap<>();

		for(String type: types){
			String file = "/shared/corpora/ner/transliteration/"+lang+"/canddata1."+type+".token.seq.p";
			t2preds.put(type, Evaluator.readSequiturOutput(file));
		}
		try{

			String outfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata1.seq.p";
			BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));

			List<String> olines = LineIO.read(origfile);
			for(int i = 0; i < olines.size(); i++){
				String[] oparts = olines.get(i).split("\t");

				String surface = oparts[0].toLowerCase();
				String type = oparts[1].toLowerCase();

				List<String> outs = Evaluator.generatePhrase(surface.split("\\s+"), t2preds.get(type));

				bw.write(outs.stream().collect(joining("\t"))+"\n");

			}
			bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

	}

	public static void evaluate(String origfile, String predfile, String lang){
		System.out.println("Evaluating "+predfile);
		WikiCandidateGenerator wcg = new WikiCandidateGenerator("en", true);
		wcg.setTokenizer(MultiLingualTokenizer.getTokenizer(lang));

		try{
			List<String> olines = LineIO.read(origfile);
			List<String> plines = LineIO.read(predfile);
			if(olines.size() != plines.size()){
				System.out.println("line # not match");
				System.exit(-1);
			}

			int correct = 0, cnt = 0, nop = 0;
			int per = 0, org = 0, loc = 0, perc = 0, orgc = 0, locc = 0;
			for(int i = 0; i < olines.size(); i++){
				if((cnt++)%100 == 0) System.out.print(cnt+"\r");
				String[] oparts = olines.get(i).split("\t");
				String[] pparts = plines.get(i).split("\t");

				String type = oparts[1];
				String gold = oparts[2];
				if(type.equals("PER"))
					per++;
				else if(type.equals("ORG"))
					org++;
				else if(type.equals("LOC"))
					loc++;

				if(plines.get(i).trim().isEmpty()){
					nop++;
					continue;
				}

				List<WikiCand> cands = wcg.genCandidate1(pparts[0]);

				Set<String> cset = cands.stream().map(x -> x.title).collect(toSet());


				if(cset.contains(gold)){
					if(type.equals("PER"))
						perc++;
					else if(type.equals("ORG"))
						orgc++;
					else if(type.equals("LOC"))
						locc++;
				}

			}
			System.out.println();
			System.out.printf("LOC: %d %d %.2f\n", locc, loc, (double)locc/loc*100);
			System.out.printf("ORG: %d %d %.2f\n", orgc, org, (double)orgc/org*100);
			System.out.printf("PER: %d %d %.2f\n", perc, per, (double)perc/per*100);
			System.out.printf("ALL: %d %d %.2f\n", locc+orgc+perc, loc+org+per, (double)(locc+orgc+perc)/(loc+org+per)*100);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
	}

	public static void main(String[] args){

        ConfigParameters.setPropValues();


		String lang = args[0];
		String predfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata1.joint";
		String origfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata1";
		String prefix = "/shared/corpora/ner/transliteration/"+lang+"/canddata1";
		//String predfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata.jeff.falign";
//		genSequiturPrediction(origfile, lang);
//		System.exit(-1);

		evaluate(origfile, prefix+".joint.best5", lang);
		evaluate(origfile, prefix+".jeff.falign", lang);
		evaluate(origfile, prefix+".jeff.palign", lang);

//		evaluate(origfile, prefix+".seq.p", lang);
//		evaluate(origfile, prefix+".seq", lang);
		//evaluate(origfile, predfile, lang);


		origfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata1.gold";
//		evaluate(origfile, prefix+".c2c", lang);
//		evaluate(origfile, prefix+".c2c.bpe", lang);
	}
}
