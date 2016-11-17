package edu.illinois.cs.cogcomp.xlwikifier;

import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ctsai12 on 10/3/16.
 */
public class ConfigParameters {

    private static final Logger logger = LoggerFactory.getLogger(ConfigParameters.class);
    public static String db_path;
    public static String dump_path;
    public static String stopword_path = "xlwikifier-data/stopwords/";
    public static Map<String, String> ner_models = new HashMap<>();
    public static Map<String, String> ranker_models = new HashMap<>();
    public static Map<String, String> ranker_ner = new HashMap<>();
    public static String tac_es_samples, tac_zh_samples;
    public static String tac_golds = "xlwikifier-data/test/tac-golds";
    public static boolean is_set = false;
    public static String search_cache;
    public static boolean use_search = false;
    public static String target_kb;
    private static String config_name;
    public static String liblinear_path = "liblinear-ranksvm-1.95";


    public static void setPropValues() {
        String default_config = "config/xlwikifier-demo.config";
        setPropValues(default_config);
    }

    public static void setPropValues(String config_file){
        ResourceManager rm = null;
        try {
            rm = new ResourceManager(config_file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(!is_set || !config_file.equals(config_name)) {
            logger.info("Loading configuration: " + config_file);
            setPropValues(rm);
            config_name = config_file;
            is_set = true;
        }
        else{
            logger.info("Config is already set to "+config_file);
        }
    }

    private static void setPropValues(ResourceManager rm) {


        // load models configs
        for (Language lang : Language.values()) {
            String l = lang.getShortName();
            String key = l + "_ner_config";
            if (rm.containsKey(key))
                ner_models.put(l, rm.getString(key).trim());

            key = l + "_ranking_model";
            if (rm.containsKey(key)) {
                ranker_models.put(l, rm.getString(key).trim());
                ranker_ner.put(l, rm.getString(key).trim());
            }

//            key = l+"_ner_ranker";
//            if(rm.containsKey(key))
//                ranker_ner.put(l, rm.getString(key).trim());
        }

        if (rm.containsKey("db_path")) {
            db_path = rm.getString("db_path").trim();
            if (!FreeBaseQuery.isloaded())
                FreeBaseQuery.loadDB(true);
        }
        else
            logger.error("db_path is required");

        if (rm.containsKey("dump_path"))
            dump_path = rm.getString("dump_path").trim();
        if (rm.containsKey("stopword_path"))
            stopword_path = rm.getString("stopword_path").trim();
        if (rm.containsKey("tac_es_samples"))
            tac_es_samples = rm.getString("tac_es_samples").trim();
        if (rm.containsKey("tac_zh_samples"))
            tac_zh_samples = rm.getString("tac_zh_samples").trim();
        if (rm.containsKey("tac_golds"))
            tac_golds = rm.getString("tac_golds").trim();
        if (rm.containsKey("use_wikisearch"))
            use_search = rm.getBoolean("use_wikisearch");
        if (rm.containsKey("search_cache"))
            search_cache = rm.getString("search_cache").trim();
        if (rm.containsKey("target_kb"))
            target_kb = rm.getString("target_kb").trim();
        if (rm.containsKey("liblinear_path"))
            liblinear_path = rm.getString("liblinear_path").trim();
    }

    public static void main(String[] args) {

        ConfigParameters param = new ConfigParameters();
        param.setPropValues();
    }
}
