package edu.illinois.cs.cogcomp.xlwikifier.research.mlner;

import edu.illinois.cs.cogcomp.core.constants.Language;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.xlwikifier.MultiLingualNER;
import edu.illinois.cs.cogcomp.xlwikifier.MultiLingualNERManager;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.mlner.NERUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Created by ctsai12 on 12/1/16.
 */
public class genWikifierFeatures {

    public static void writeWikifierFeatures(QueryDocument doc, String outpath) {
        String out = "";

        TextAnnotation ta = doc.getTextAnnotation();

        if(doc.mentions == null){
            System.out.println("mentions null! "+doc.getDocID());
        }
        if(ta == null){
            System.out.println("ta null! "+doc.getDocID());
        }
        if(ta.getTokens() == null)
            System.out.println("tokens null! "+doc.getDocID());
        if(ta.getTokens().length!=doc.mentions.size()){
            System.out.println("Size doesn't match!");
            System.out.println(ta.getTokens().length+" "+doc.mentions.size());
        }

        int midx = 0;
        for(int i = 0; i < ta.getTokens().length; i++){
            IntPair offsets = ta.getTokenCharacterOffset(i);
            ELMention m = null;
            int mention_id = -1;
            for(int j = midx; j < doc.mentions.size(); j++) {
                ELMention mention = doc.mentions.get(j);
                if (offsets.getFirst() == mention.getStartOffset() && offsets.getSecond() == mention.getEndOffset()) {
                    m = mention;
                    mention_id = j;
                    midx = j+1;
                    break;
                }
            }
//                System.out.println("a: "+(System.currentTimeMillis() - start));
//                start = System.currentTimeMillis();

            if(m == null){
                System.out.println("cound't match "+offsets+" "+ta.getToken(i));
                continue;
            }

            if(i>0 && ta.getSentenceId(i) != ta.getSentenceId(i-1)) {
                out += "\n";
            }

            Map<String, Double> fmap = m.ner_features;
            out += m.getType()+"\tx\tx\tx\tx";
            out += "\t"+m.getSurface()+"\tx\tx\tx\tx";
            for(String key: fmap.keySet())
                out += "\t"+key+":"+fmap.get(key);
            out += "\n";
        }

        try {
            FileUtils.writeStringToFile(new File(outpath, doc.getDocID()), out, "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void setBIOGolds(QueryDocument doc, List<ELMention> golds){
        for(ELMention m: doc.mentions){
            boolean get = false;
            for(ELMention gold: golds){
                if(m.getStartOffset() >= gold.getStartOffset()
                        && m.getEndOffset() <= gold.getEndOffset()){
                    if(m.getStartOffset() == gold.getStartOffset())
                        m.setType("B-"+gold.getType());
                    else
                        m.setType("I-"+gold.getType());
                    get = true;
                    break;
                }
            }
            if(!get)
                m.setType("O");
        }
    }

    public static void main(String[] args) {

        Language lang = Language.Hungarian;

        String indir = "/shared/corpora/ner/lorelei/hu/All";
        String outdir = "/shared/corpora/ner/wikifier-features/hu/All";

        ColumnFormatReader reader = new ColumnFormatReader();
        List<QueryDocument> docs = reader.readDir(indir, false);


        NERUtils nerutils = new NERUtils(lang.getCode());

        int cnt = 0;
        for(QueryDocument doc: docs){
            System.out.println(cnt++);

            List<ELMention> golds = doc.mentions;
            nerutils.wikifyNgrams(doc);
            setBIOGolds(doc, golds);
            writeWikifierFeatures(doc, outdir+"/"+doc.getDocID());
        }


    }
}
