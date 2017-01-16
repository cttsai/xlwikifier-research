package edu.illinois.cs.cogcomp.xlwikifier.research;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import org.apache.commons.io.FileUtils;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import org.apache.commons.io.FileUtils;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.tokenizers.MultiLingualTokenizer;
import edu.illinois.cs.cogcomp.tokenizers.Tokenizer;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.MediaWikiSearch;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import edu.stanford.nlp.util.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import edu.illinois.cs.cogcomp.xlwikifier.mlner.NERUtils;


public class Squad{

	private NERUtils nerutils;
	private Map<String, Set<String>> t2fbtypes;
	private Map<String, Set<String>> t2wikitypes;
	private String outdir;

	public Squad(){
        ConfigParameters.setPropValues();
		nerutils = new NERUtils("en");
		t2fbtypes = new HashMap<>();
		t2wikitypes = new HashMap<>();
	}

    public void wikifyNgrams(QueryDocument doc, int cnt) {

        List<ELMention> prevm = new ArrayList<>();
		String mout = "";
        for (int n = 4; n > 0; n--) {
            doc.mentions = nerutils.getNgramMentions(doc, n);
			for (int j = 0; j < doc.mentions.size(); j++) {
				ELMention m = doc.mentions.get(j);

				String surface = m.getSurface().toLowerCase();

				if (NumberUtils.isNumber(surface.trim())) continue;

				nerutils.wikifyMention(m, n);
				if(!m.getWikiTitle().startsWith("NIL")){
					mout += m.getSurface()+"\t"+m.getStartOffset()+"\t"+m.getEndOffset()+"\t"+m.getWikiTitle()+"\t"+m.getMid()+"\n";
				}

				if(!m.getMid().startsWith("NIL") && !t2fbtypes.containsKey(m.getMid())){
					Set<String> types = new HashSet<>(FreeBaseQuery.getTypesFromMid(m.getMid()));
					t2fbtypes.put(m.getMid(), types);
				}
				String title = m.getWikiTitle();
				if(!title.startsWith("NIL") && !t2wikitypes.containsKey(title)){
                    Set<String> types = new HashSet<>( MediaWikiSearch.getCategories(title, "en"));
					t2wikitypes.put(title,types);
				}
			}
        }
		try{
			FileUtils.writeStringToFile(new File(outdir+"ngram/"+cnt), mout, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public void run(String file){
		outdir = file+".results/";
		try{
			List<String> lines = LineIO.read(file);
			String text = "";
			int cnt = 0;
			for(int i = 0; i < lines.size(); i++){
				if(i == lines.size()-1 || lines.get(i+1).startsWith("P:")){
					System.out.print(cnt+"\r");

	            	FileUtils.writeStringToFile(new File(outdir+"text/"+cnt), text, "UTF-8");

					Tokenizer tokenizer = MultiLingualTokenizer.getTokenizer("en");
					TextAnnotation ta = tokenizer.getTextAnnotation(text);
					QueryDocument doc = new QueryDocument("");
					doc.text = text;
					doc.setTextAnnotation(ta);
					wikifyNgrams(doc, cnt);

					text = "";
					cnt++;
				}

				String line = lines.get(i);
				text += line.substring(3,line.length())+"\n";
			}

			String tout = "";
			for(String key: t2fbtypes.keySet()){
				tout += key;
				for(String type: t2fbtypes.get(key))
					tout += "\t"+type;
				tout += "\n";
			}
			FileUtils.writeStringToFile(new File(outdir+"freebase.types"), tout, "UTF-8");

			tout = "";
			for(String key: t2wikitypes.keySet()){
				tout += key;
				for(String type: t2wikitypes.get(key))
					tout += "\t"+type;
				tout += "\n";
			}
			FileUtils.writeStringToFile(new File(outdir+"wiki.types"), tout, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	public static void main(String[] args){

		String file = "/home/ctsai12/squadQuestions-train.txt";
		Squad s = new Squad();
		s.run(file);

	}
}
