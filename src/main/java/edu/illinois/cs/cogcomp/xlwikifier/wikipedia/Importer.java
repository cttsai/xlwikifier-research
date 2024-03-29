package edu.illinois.cs.cogcomp.xlwikifier.wikipedia;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.core.Ranker;
import edu.illinois.cs.cogcomp.xlwikifier.core.TFIDFManager;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Sentence;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.wiki.parsing.MLWikiDumpFilter;
import edu.illinois.cs.cogcomp.wiki.parsing.processors.PageMeta;
import edu.illinois.cs.cogcomp.xlwikifier.core.WikiCandidateGenerator;
import info.bliki.wiki.dump.WikiArticle;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * Created by ctsai12 on 1/25/16.
 */
public class Importer {
    private static Logger logger = LoggerFactory.getLogger(Importer.class);
    private String lang = "sv";
    //    private static String date = "20151123";
    private String date = "20160801";
    private String dumpfile, pagefile, langfile, redirectfile;
    private String docdir, candfile, textfile;

    public Importer(String lang, String date) {
        this.lang = lang;
        this.date = date;
        setPath();
    }

    public void setPath() {
        String dumpdir = ConfigParameters.dump_path + lang;
        if (!(new File(dumpdir).exists()))
            new File(dumpdir).mkdir();
        dumpfile = dumpdir + "/" + lang + "wiki-" + date + "-pages-articles.xml.bz2";
        pagefile = dumpdir + "/" + lang + "wiki-" + date + "-page.sql.gz";
        langfile = dumpdir + "/" + lang + "wiki-" + date + "-langlinks.sql.gz";
        redirectfile = dumpdir + "/" + lang + "wiki-" + date + "-redirect.sql.gz";
        docdir = dumpdir + "/docs/";
        candfile = dumpdir + "/links";
        textfile = dumpdir + "/sg.withtitle";

    }


    public void downloadDump() {
        try {
            logger.info("Downloading " + lang + " wikipedia dump...");
            URL url = new URL("https://dumps.wikimedia.org/" + lang + "wiki/" + date + "/" + lang + "wiki-" + date + "-pages-articles.xml.bz2");
            System.out.println(url);
            FileUtils.copyURLToFile(url, new File(dumpfile));
            logger.info("Downloading page sql file...");
            url = new URL("https://dumps.wikimedia.org/" + lang + "wiki/" + date + "/" + lang + "wiki-" + date + "-page.sql.gz");
            System.out.println(url);
            FileUtils.copyURLToFile(url, new File(pagefile));
            logger.info("Downloading lang link file...");
            url = new URL("https://dumps.wikimedia.org/" + lang + "wiki/" + date + "/" + lang + "wiki-" + date + "-langlinks.sql.gz");
            System.out.println(url);
            FileUtils.copyURLToFile(url, new File(langfile));
            logger.info("Downloading redirect file...");
            url = new URL("https://dumps.wikimedia.org/" + lang + "wiki/" + date + "/" + lang + "wiki-" + date + "-redirect.sql.gz");
            System.out.println(url);
            FileUtils.copyURLToFile(url, new File(redirectfile));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void generatePlainText() throws IOException, SAXException {
        logger.info("Parsing wikidump " + dumpfile);
        String dumpdir = ConfigParameters.dump_path + lang;
        String outfile = dumpdir + "/plain.text";
        StringBuilder doc_text = new StringBuilder();
        MLWikiDumpFilter filter = new MLWikiDumpFilter(20) {
            @Override
            public void processAnnotation(WikiArticle page, PageMeta meta, TextAnnotation ta) throws Exception {
                if (ta == null || meta.isRedirect() || meta.isDisambiguationPage())
                    return;

                // output wikidata
//                if(wikipedia_view.getConstituents().size() < 5 || page.getTitle().contains("/"))
                if (page.getTitle().contains("/"))
                    return;

                String doc = "";
                for (int j = 0; j < ta.getNumberOfSentences(); j++) {
                    Sentence s = ta.getSentence(j);
                    String text = s.getTokenizedText().trim();
                    if (!text.isEmpty() && !text.startsWith("<"))
                        doc += s.getTokenizedText().trim() + "\n";
                }

                synchronized (doc_text) {
                    //if(doc.length() > 10)
                    doc_text.append(doc + "\n");
                    if (doc_text.length() > 1000000) {
                        BufferedWriter bw = new BufferedWriter(new FileWriter(outfile, true));
                        bw.write(doc_text.toString());
                        bw.close();
                        doc_text.delete(0, doc_text.length());
                    }
                }
            }
        };
        filter.setLang(lang);
        MLWikiDumpFilter.parseDump(dumpfile, filter);

        BufferedWriter bw = new BufferedWriter(new FileWriter(outfile, true));
        bw.write(doc_text.toString());
        bw.close();
    }

    public void parseWikiDump() throws IOException, SAXException {

        logger.info("Parsing wikidump " + dumpfile);
        if(new File(textfile).exists()){
            logger.warn(textfile+" exists!");
            System.exit(-1);
        }
        if(new File(candfile).exists()){
            logger.warn(candfile+" exists!");
            System.exit(-1);
        }
        List<String> docs = new ArrayList<>();
        StringBuilder doc_text = new StringBuilder();
        StringBuilder cand_pair = new StringBuilder();
        List<String> titles = new ArrayList<>();
        MLWikiDumpFilter filter = new MLWikiDumpFilter(20) {
            @Override
            public void processAnnotation(WikiArticle page, PageMeta meta, TextAnnotation ta) throws Exception {
                if (ta == null || !ta.hasView(ViewNames.WIKIFIER)
                        || meta.isRedirect() || meta.isDisambiguationPage())
                    return;
                SpanLabelView wikipedia_view = (SpanLabelView) ta.getView(ViewNames.WIKIFIER);


                // for candidate generation
                for (Constituent c : wikipedia_view.getConstituents()) {
                    synchronized (cand_pair) {
                        cand_pair.append(c.getSurfaceForm() + "\t" + c.getLabel() + "\n");

                        if (cand_pair.length() > 100000) {
                            BufferedWriter bw1 = new BufferedWriter(new FileWriter(candfile, true));
                            bw1.write(cand_pair.toString());
                            bw1.close();
                            cand_pair.delete(0, cand_pair.length());
                        }
                    }
                }

                // output wikidata
//                if(wikipedia_view.getConstituents().size() < 5 || page.getTitle().contains("/"))
                if (page.getTitle().contains("/"))
                    return;
                synchronized (titles) {
                    titles.add(page.getTitle());
                }
                FileUtils.writeStringToFile(new File(docdir + "plain/" + page.getTitle()), ta.getText(), "UTF-8");
                String annot_str = "";
                SpanLabelView tokenview = (SpanLabelView) ta.getView(ViewNames.TOKENS);
                for (Constituent c : tokenview.getConstituents()) {
                    annot_str += c.getSurfaceForm() + "\t" + c.getStartCharOffset() + "\t" + c.getEndCharOffset() + "\n";
                }
                for (Constituent c : wikipedia_view.getConstituents())
                    annot_str += "#" + c.getLabel() + "\t" + c.getStartCharOffset() + "\t" + c.getEndCharOffset() + "\n";
                FileUtils.writeStringToFile(new File(docdir + "annotation/" + page.getTitle()), annot_str, "UTF-8");

                // start stores the offset of the next hyperlinked text
                int idx = 0;
                int start, end = 0;
                if (wikipedia_view.getConstituents().size() == 0) {
                    start = ta.getTokens().length;
                } else {
                    start = wikipedia_view.getConstituents().get(idx).getStartSpan();
                    end = wikipedia_view.getConstituents().get(idx).getEndSpan();
                }
//                start = ta.getTokens().length; // TODO

                String[] tokens = ta.getTokens();
                String doc = "";
                for (int j = 0; j < ta.getNumberOfSentences(); j++) {
                    Sentence s = ta.getSentence(j);
                    String sen = "";
                    for (int i = s.getStartSpan(); i < s.getEndSpan(); i++) {
                        if (i < start) {   // before the next hyperlinked phrase
                            sen += tokens[i] + " ";
                        } else if (i < end) { // inside a hyperlinked phrase
                            sen += "TITLE_" + wikipedia_view.getConstituents().get(idx).getLabel() + " ";
                            i = end - 1;
                            idx++;
                            if (idx == wikipedia_view.getConstituents().size())
                                start = tokens.length;
                            else {
                                start = wikipedia_view.getConstituents().get(idx).getStartSpan();
                                end = wikipedia_view.getConstituents().get(idx).getEndSpan();
                            }
                        }
                    }
                    if (sen.trim().length() > 5)
                        doc += sen.trim() + "\n";
                }
                synchronized (doc_text) {
                    //if(doc.length() > 10)
                    doc_text.append(doc + "\n");
                    if (doc_text.length() > 1000000) {
                        BufferedWriter bw = new BufferedWriter(new FileWriter(textfile, true));
                        bw.write(doc_text.toString());
                        bw.close();
                        doc_text.delete(0, doc_text.length());
                    }
                }
            }
        };
        filter.setLang(lang);
        MLWikiDumpFilter.parseDump(dumpfile, filter);

        BufferedWriter bw = new BufferedWriter(new FileWriter(textfile, true));
        bw.write(doc_text.toString());
        bw.close();

        BufferedWriter bw1 = new BufferedWriter(new FileWriter(candfile, true));
        bw1.write(cand_pair.toString());
        bw1.close();

        Collections.shuffle(titles, new Random(0));
        FileUtils.writeStringToFile(new File(docdir + "file.list.rand"), titles.stream().collect(joining("\n")), "UTF-8");
    }

    public void importLangLinks() {
        logger.info("Importing language links...");
        LangLinker ll = new LangLinker(lang, false);
        ll.populateDBNew(lang, langfile, pagefile);
    }

    public void importCandidates() {
        logger.info("Importing into candidate DB...");
        WikiCandidateGenerator wcg = new WikiCandidateGenerator(lang, false);
        wcg.populateDB(lang, redirectfile, pagefile, candfile);
    }

    public void importTFIDF() {
        logger.info("Importing into TFIDF DB...");
        TFIDFManager tm = new TFIDFManager();
        try {
            tm.populateDB(lang, textfile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getMostFreqWords(){

        Map<String, Integer> wcnt = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(textfile));
            String line;
            while ((line = br.readLine())!= null) {
                for (String token : line.toLowerCase().split("\\s+")) {
                    if (!wcnt.containsKey(token))
                        wcnt.put(token, 1);
                    else
                        wcnt.put(token, wcnt.get(token) + 1);
                }
            }
            br.close();

            List<String> sorted = wcnt.entrySet().stream()
                    .sorted((x1, x2) -> Integer.compare(x2.getValue(), x1.getValue()))
                    .map(x -> x.getKey()).filter(x -> !x.startsWith("title_"))
                    .collect(Collectors.toList());

            String out = sorted.subList(0, 50).stream().collect(joining("\n"));
            FileUtils.writeStringToFile(new File(ConfigParameters.dump_path+"/"+lang, "stopwords."+lang), out, "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        try {
            ConfigParameters.setPropValues(args[2]);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

//        Importer importer = new Importer(args[0], args[1]);

        List<String> langs = new ArrayList<>();
        langs.add("am");
//        try {
//            langs = LineIO.read("import-langs");
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        String date = "20160801";
        String date = "20170601";
//        String date = "20170320";
        for (String lang : langs) {
            lang = lang.trim();
//            if(lang.equals("ceb") || lang.equals("war") || lang.equals("pl")) continue;
            Importer importer = new Importer(lang, date);
            try {
//                importer.downloadDump();
                importer.parseWikiDump();
                importer.importLangLinks();
                importer.importCandidates();
                importer.importTFIDF();
                importer.getMostFreqWords();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
