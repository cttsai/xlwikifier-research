package edu.illinois.cs.cogcomp.xlwikifier.wikipedia;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * Created by ctsai12 on 12/4/15.
 */
public class TitleGender {

    private DB db;
    public HTreeMap<String, Integer> male_cnt;
    public HTreeMap<String, Integer> female_cnt;
    private String lang;
    private static Logger logger = LoggerFactory.getLogger(TitleGender.class);
    private static Map<String, TitleGender> lang_map;
    private static TitleGender en_map;

    public TitleGender(String lang, boolean read_only) {

        if(!lang.equals("en"))
            en_map = TitleGender.getTitleGender("en");

        String dbfile = ConfigParameters.db_path + "/titlegender/" + lang;

        if (read_only) {
            db = DBMaker.fileDB(dbfile)
                    .fileChannelEnable()
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
            male_cnt = db.hashMap("male_cnt")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INTEGER)
                    .open();
            female_cnt = db.hashMap("femail_cnt")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INTEGER)
                    .open();

        } else {
            db = DBMaker.fileDB(dbfile)
                    .closeOnJvmShutdown()
                    .make();
            male_cnt = db.hashMap("male_cnt")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INTEGER)
                    .create();
            female_cnt = db.hashMap("femail_cnt")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.INTEGER)
                    .create();
        }


        this.lang = lang;

    }

    public TitleGender() {

    }

    public static TitleGender getTitleGender(String lang){
        if(lang_map == null)
            lang_map = new HashMap<>();

        if(!lang_map.containsKey(lang)){
            logger.info("Initializing Title Gender Map "+lang);
            TitleGender ll;
            ll = new TitleGender(lang, true);
            lang_map.put(lang, ll);
        }

        return lang_map.get(lang);
    }

    public void closeDB() {
        if (db != null && !db.isClosed()) {
            db.commit();
            db.close();
        }
    }

    public void populateDB() {

        String nominal_dir = "/shared/corpora/ner/pronominal_exp/"+lang;

        Set<String> males;
        Set<String> females;
        try {
            ArrayList<String> lines = LineIO.read(nominal_dir + "/male");
            males = lines.stream().map(x -> x.trim().toLowerCase()).collect(Collectors.toSet());
            lines = LineIO.read(nominal_dir + "/female");
            females = lines.stream().map(x -> x.trim().toLowerCase()).collect(Collectors.toSet());
            ArrayList<String> titles = LineIO.read(ConfigParameters.dump_path + "/" + lang + "/docs/file.list.rand");

            int cnt = 0;
            for(File f: new File(ConfigParameters.dump_path + "/" + lang + "/docs/annotation").listFiles()){
                String title = f.getName();
                int mc = 0, fc = 0;
                for(String line: LineIO.read(f.getAbsolutePath())){
                    if(line.startsWith("#")) continue;
                    String[] tokens = line.split("\t");
                    String word = tokens[0].toLowerCase();
                    if(males.contains(word))
                        mc++;
                    if(females.contains(word))
                        fc++;
                }
                male_cnt.put(formatTitle(title), mc);
                female_cnt.put(formatTitle(title), fc);
                if(cnt++%1000 == 0)
                    System.out.println(cnt);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        closeDB();
    }

    public String formatTitle(String title) {
        String[] tokens = title.toLowerCase().split("\\s+");
        return Arrays.asList(tokens).stream().collect(joining("_"));
    }

    public boolean getGender(String title){
        title = formatTitle(title);
        int mc = 0, fc = 0;
        if(male_cnt.containsKey(title))
            mc = male_cnt.get(title);
        if(female_cnt.containsKey(title))
            fc = female_cnt.get(title);
//        System.out.println(mc+" "+fc);

        return mc >= fc;
    }

    public void printPersonGazetteer(String outdir) throws IOException {

        BufferedWriter bwm = new BufferedWriter(new FileWriter(outdir+"/male.names"));
        BufferedWriter bwf = new BufferedWriter(new FileWriter(outdir+"/female.names"));

        for(Object key: male_cnt.keySet()){
            String title = key.toString();
            List<String> types = FreeBaseQuery.getTypesFromTitle(title, "en");
            if(types.contains("people.person")){

                int mc = male_cnt.get(title);
                int fc = 0;

                if(female_cnt.containsKey(title))
                    fc = female_cnt.get(title);

                title = title.replaceAll("_", " ");
                int idx = title.indexOf("(");
                if(idx > 0)
                    title = title.substring(0,idx).trim();

                if(mc > fc)
                    bwm.write(title+"\n");
                else if(fc > mc)
                    bwf.write(title+"\n");
            }
        }
        bwm.close();
        bwf.close();
    }


    public static void main(String[] args) {

        try {
            ConfigParameters.setPropValues();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }




//        TitleGender tg = new TitleGender("es", false);
//        tg.populateDB();

        TitleGender tf = getTitleGender("en");
//        tf.getGender("united_states");

        try {
            tf.printPersonGazetteer("/shared/corpora/ner/pronominal_exp/en/gazetteers");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
