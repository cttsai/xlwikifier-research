package edu.illinois.cs.cogcomp.xlwikifier.research.mlner;

import edu.illinois.cs.cogcomp.xlwikifier.MultiLingualNERManager;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;

import java.util.List;

/**
 * Created by ctsai12 on 12/1/16.
 */
public class genWikifierFeatures {


    public static void main(String[] args) {

        String lang = ""

        String indir = "";
        ColumnFormatReader reader = new ColumnFormatReader();
        List<QueryDocument> docs = reader.readDir(indir, false);

        annotator = MultiLingualNERManager.buildNerAnnotator()


    }
}
