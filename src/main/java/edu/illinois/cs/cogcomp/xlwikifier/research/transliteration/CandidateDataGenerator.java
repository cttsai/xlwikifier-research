package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.LangLinker;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.WikiDocReader;
import org.h2.util.StringUtils;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toSet;


/**
 * This class uses title mapping between English and the target language to
 * generate gazetters for the target language.
 *
 * Created by ctsai12 on 3/9/16.
 */
public class CandidateDataGenerator {

    private String dir = "/shared/corpora/ner/gazetteers/";

	public static void checkInterCoverage(String lang){

		try {
			ConfigParameters.setPropValues("config/xlwikifier-demo.config");
		} catch (IOException e) {
			e.printStackTrace();
		}

		String trans_dir = "/shared/corpora/ner/transliteration/"+lang;
		Map<String, Set<String>> type2train= new HashMap<>();
		List<String> types = Arrays.asList("loc", "org", "per");
		Map<String, Set<String>> out = new HashMap<>();
		for(String type: types){

			out.put(type, new HashSet<>());

			String train_file = trans_dir+"/"+type+"/train.select";
			try {
				type2train.put(type, LineIO.read(train_file).stream()
						.map(x -> x.split("\t")[0])
						.collect(toSet()));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		String query_lang = lang;
		if(lang.equals("zh")) query_lang = "zh-cn";

		LangLinker ll = LangLinker.getLangLinker(lang);

		WikiDocReader reader = new WikiDocReader();
		List<QueryDocument> docs = reader.readWikiDocs(lang, 10000);
		int has_en = 0, total = 0;
		int cnt = 0;
		for(QueryDocument doc: docs){
			if((cnt++)%100 == 0) System.out.print(cnt+"\r");

			for(ELMention m: doc.mentions){
				String entitle = ll.translateToEn(m.gold_wiki_title, lang);
				if(entitle == null) {
					total++;
					continue;
				}


				String mid = FreeBaseQuery.getMidFromTitle(entitle, "en");
				List<String> ts = new ArrayList<>();
				if(mid != null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));
				mid = FreeBaseQuery.getMidFromTitle(m.gold_wiki_title, query_lang);
				if(mid!=null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));

				String netype = "";
				if(ts.contains("people.person")) netype = "PER";
				else if(ts.contains("organization.organization")) netype = "ORG";
				else if(ts.contains("location.location")) netype = "LOC";
				else continue;

				if(type2train.get(netype.toLowerCase()).contains(m.getSurface().toLowerCase()))
					continue;

				if(StringUtils.isNumber(entitle.substring(0, 1))) continue;
				if(StringUtils.isNumber(m.getSurface().substring(0, 1))) continue;

				if(entitle.contains("/") || m.getSurface().contains("/")) continue;

				if(entitle.trim().isEmpty()) continue;

				String s = m.getSurface();
				if(s.isEmpty() || s.split("\\s+").length > 4) continue;

				String t = entitle;
				if(s.toLowerCase().equals(t.replaceAll("_"," ")))
					continue;

				has_en++;
				total++;
			}
		}
		System.out.println(has_en+" "+total+" "+(float)has_en/total);
	}

    public static void genCandData(String lang){

		try {
			ConfigParameters.setPropValues();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String trans_dir = "/shared/corpora/ner/transliteration/"+lang;
		Map<String, Set<String>> type2train= new HashMap<>();
		List<String> types = Arrays.asList("loc", "org", "per");
		Map<String, Set<String>> out = new HashMap<>();
		for(String type: types){

			out.put(type, new HashSet<>());

			String train_file = trans_dir+"/"+type+"/train.select";
			try {
				type2train.put(type, LineIO.read(train_file).stream()
					.map(x -> x.split("\t")[0])
					.collect(toSet()));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

        String query_lang = lang;
        if(lang.equals("zh")) query_lang = "zh-cn";

        LangLinker ll = LangLinker.getLangLinker(lang);

        WikiDocReader reader = new WikiDocReader();
        List<QueryDocument> docs = reader.readWikiDocs(lang, 10000);
		int cnt = 0;
		for(QueryDocument doc: docs){
			if((cnt++)%100 == 0) System.out.print(cnt+"\r");

			for(ELMention m: doc.mentions){
				String entitle = ll.translateToEn(m.gold_wiki_title, lang);
				if(entitle == null) continue;

				String mid = FreeBaseQuery.getMidFromTitle(entitle, "en");
				List<String> ts = new ArrayList<>();
				if(mid != null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));
				mid = FreeBaseQuery.getMidFromTitle(m.gold_wiki_title, query_lang);
				if(mid!=null) ts.addAll(FreeBaseQuery.getTypesFromMid(mid));

				String netype = "";
				if(ts.contains("people.person")) netype = "PER";
				else if(ts.contains("organization.organization")) netype = "ORG";
				else if(ts.contains("location.location")) netype = "LOC";
				else continue;

				if(type2train.get(netype.toLowerCase()).contains(m.getSurface().toLowerCase()))
					continue;

				if(StringUtils.isNumber(entitle.substring(0, 1))) continue;
				if(StringUtils.isNumber(m.getSurface().substring(0, 1))) continue;

				if(entitle.contains("/") || m.getSurface().contains("/")) continue;

				if(entitle.trim().isEmpty()) continue;

				String s = m.getSurface();
				/*
				int idx = s.indexOf("(");
				if(idx > -1)
					s = s.substring(0, idx);
				*/
				if(s.isEmpty() || s.split("\\s+").length > 4) continue;


				String t = entitle;
				/*
				idx = t.indexOf("(");
				if(idx > -1)
					t = t.substring(0, idx-1);
				*/
				if(s.toLowerCase().equals(t.replaceAll("_"," ")))
						continue;

				out.get(netype.toLowerCase()).add(s+"\t"+netype+"\t"+entitle);

			}
		}

		String outfile = "/shared/corpora/ner/transliteration/"+lang+"/canddata1";

		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));

			for(String type: out.keySet()){
				for(String line: out.get(type))
					bw.write(line+"\n");
			}

			bw.close();
		} catch (IOException e){
			e.printStackTrace();
		}

    }

    public static void main(String[] args) {

        List<String> langs = Arrays.asList("tr", "tl", "bn", "ta", "es");
//        List<String> langs = Arrays.asList("zh", "fr", "it", "he", "ar", "ur", "th");
//       String lang = args[0];

//		genCandData(lang);
        for(String lang: langs)
			checkInterCoverage(lang);
    }
}
