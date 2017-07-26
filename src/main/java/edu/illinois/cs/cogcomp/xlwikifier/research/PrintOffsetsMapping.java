package edu.illinois.cs.cogcomp.xlwikifier.research;

import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.evaluation.TACDataReader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by ctsai12 on 7/26/17.
 */
public class PrintOffsetsMapping {

    public static void main(String[] args) {

        try {
            ConfigParameters.setPropValues("config/xlwikifier-tac.config");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        TACDataReader reader = new TACDataReader(false);
        List<QueryDocument> docs = null;
        try {
            docs = reader.readDocs("/shared/corpora/corporaWeb/tac/2017/LDC2017E25_TAC_KBP_2017_Evaluation_Source_Corpus/data/spa/nw/", "es");
            docs.addAll(reader.readDocs("/shared/corpora/corporaWeb/tac/2017/LDC2017E25_TAC_KBP_2017_Evaluation_Source_Corpus/data/spa/df/", "es"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String outdir = "/shared/bronte/Tinkerbell/EDL/2017_spa_charmap";
        for(QueryDocument doc: docs){

            String out = "";
            Map<Integer, Integer> sm = doc.getXmlhandler().getPXMappingStart();
            Map<Integer, Integer> em = doc.getXmlhandler().getPXMappingEnd();


        }
    }

}
