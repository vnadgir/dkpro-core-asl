/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.mstparser;

import static java.util.Arrays.asList;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.uima.fit.util.JCasUtil.exists;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;
import static org.apache.uima.util.Level.INFO;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import mstparser.DependencyInstance;
import mstparser.DependencyParser;
import mstparser.DependencyPipe;
import mstparser.DependencyPipe2O;
import mstparser.ParserOptions;

import org.apache.commons.io.IOUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.SingletonTagset;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CompressionUtils;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ModelProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

/**
 * Wrapper for the MSTParser (<b>high memory requirements</b>). More information about the parser
 * can be found <a href="http://www.seas.upenn.edu/~strctlrn/MSTParser/MSTParser.html">here</a><br>
 * and<br>
 * and <a href="http://sourceforge.net/projects/mstparser/">here</a><br>
 * The MSTParser models tend to be very large, e.g. the 
 * <a href="http://nlp.stanford.edu/software/stanford-dependencies.shtml">Eisner</a> model is about
 * 600 MB uncompressed. With this model, parsing a simple sentence with MSTParser requires about
 * 3 GB heap memory.
 * 
 * @author beinborn
 * @author zesch
 */
@TypeCapability(
        inputs = {
            "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
            "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
            "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS" },
        outputs = {
            "de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency" })
public class MstParser
    extends JCasConsumer_ImplBase
{
    /**
     * Use this language instead of the document language to resolve the model.
     */
    public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
    @ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
    protected String language;

    /**
     * Override the default variant used to locate the model.
     */
    public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
    @ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
    protected String variant;

    /**
     * Load the model from this location instead of locating the model automatically.
     */
    public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
    @ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
    protected String modelLocation;

    /**
     * Log the tag set(s) when a model is loaded.
     *
     * Default: {@code false}
     */
    public static final String PARAM_PRINT_TAGSET = ComponentParameters.PARAM_PRINT_TAGSET;
    @ConfigurationParameter(name = PARAM_PRINT_TAGSET, mandatory = true, defaultValue = "false")
    protected boolean printTagSet;

    /**
     * Load the dependency to UIMA type mapping from this location instead of locating
     * the mapping automatically.
     */
    public static final String PARAM_DEPENDENCY_MAPPING_LOCATION = ComponentParameters.PARAM_DEPENDENCY_MAPPING_LOCATION;
    @ConfigurationParameter(name = PARAM_DEPENDENCY_MAPPING_LOCATION, mandatory = false)
    protected String dependencyMappingLocation;

    private ModelProviderBase<DependencyParser> modelProvider;
    private MappingProvider mappingProvider;

    /**
     * Initializes the MSTParser and creates a ModelResourceProvicer
     *
     * @throws ResourceInitializationException
     *             Cannot be initialized
     */
    @Override
    public void initialize(UimaContext context)
        throws ResourceInitializationException
    {
        super.initialize(context);

        // the modelProvider reads in the model and produces a parser
        modelProvider = new ModelProviderBase<DependencyParser>()
        {
            {
                setContextObject(MstParser.this);

                setDefault(ARTIFACT_ID, "${groupId}.mstparser-model-parser-${language}-${variant}");

                setDefaultVariantsLocation("${package}/lib/parser-default-variants.map");
                setDefault(LOCATION,
                        "classpath:${package}/lib/parser-${language}-${variant}.properties");

                setOverride(LOCATION, modelLocation);
                setOverride(LANGUAGE, language);
                setOverride(VARIANT, variant);
            }

            @Override
            protected DependencyParser produceResource(URL aUrl)
                throws IOException
            {
                // mst.ParserOptions needs a String as argument
                ParserOptions options = new ParserOptions(new String[] {});
                options.test = true;
                options.train = false;
                options.trainfile = "";
                options.eval = false;
                options.format = "MST";
                options.goldfile = "";
                options.testfile = "";
                getLogger().info("Retrieving model");
                options.modelName = aUrl.toString();

                DependencyPipe pipe = options.secondOrder ? new DependencyPipe2O(options)
                        : new DependencyPipe(options);

                DependencyParser dp = new DependencyParser(pipe, options);
                
                InputStream is = null;
                try {
                    is = CompressionUtils.getInputStream(aUrl.getFile(), aUrl.openStream());
                    dp.loadModel(is);
                }
                finally {
                    closeQuietly(is);
                }
                
                Properties metadata = getResourceMetaData();
                SingletonTagset depTags = new SingletonTagset(
                        Dependency.class, metadata.getProperty("dependency.tagset"));
                depTags.addAll(asList(pipe.types));
                addTagset(depTags);
                
                if (printTagSet) {
                    getContext().getLogger().log(INFO, getTagset().toString());
                }

                return dp;
            };
        };
        
        mappingProvider = new MappingProvider();
        mappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/" +
                "core/api/syntax/tagset/${language}-${dependency.tagset}-dependency.map");
        mappingProvider.setDefault(MappingProvider.BASE_TYPE, Dependency.class.getName());
        mappingProvider.setDefault("dependency.tagset", "default");
        mappingProvider.setOverride(MappingProvider.LOCATION, dependencyMappingLocation);
        mappingProvider.setOverride(MappingProvider.LANGUAGE, language);
        mappingProvider.addImport("dependency.tagset", modelProvider);
    }

    /**
     * Processes the given text using the MSTParser. As the MSTParser expects an input file, a
     * temporary file is created.
     *
     * @param jcas
     *            The JCas containing the textual input
     * @throws AnalysisEngineProcessException
     *             No parse created
     */
    @Override
    public void process(JCas jcas)
        throws AnalysisEngineProcessException
    {
        CAS cas = jcas.getCas();
        modelProvider.configure(cas);
        mappingProvider.configure(cas);
        DependencyParser dp = modelProvider.getResource();

        // If there are no sentences or tokens in the CAS, skip it.
        if (!exists(jcas, Sentence.class) || !exists(jcas, Token.class)) {
            return;
        }
        
        // currently the parser needs a file as input, it cannot yet work directly with the
        // cas-structure
        try {
            String tempfile = generateTempInputFile(jcas);
            dp.options.testfile = tempfile;
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        // Run the parser
        // dp.getParses() is a method that we added to the MSTParser codebase, it returns a list of
        // parses. Originally this was dp.outputParses() and the method wrote the parses into a
        // file.
        // The old method is still available.
        List<DependencyInstance> parsedInstances;
        try {
            parsedInstances = dp.getParses();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        List<Sentence> sentences = new ArrayList<Sentence>(select(jcas, Sentence.class));

        for (int instanceIndex = 0; instanceIndex < parsedInstances.size(); instanceIndex++) {

            DependencyInstance instance = parsedInstances.get(instanceIndex);
            Sentence sentence = sentences.get(instanceIndex);

            List<Token> tokens = new ArrayList<Token>(selectCovered(jcas, Token.class, sentence));

            // iterate through tokens
            for (int formsIndex = 0; formsIndex < instance.forms.length; formsIndex++) {
                Token token = tokens.get(formsIndex);

                // get dependency relation and head information for token
                int head = instance.heads[formsIndex];

                // write dependency information as annotation to JCas
                Type depRel = mappingProvider.getTagType(instance.deprels[formsIndex]);
                Dependency dep = (Dependency) cas.createFS(depRel);
                dep.setDependencyType(instance.deprels[formsIndex]);

                // the dependent is the token itself
                dep.setDependent(token);
                if (head > 0) {
                    dep.setGovernor(tokens.get(head - 1));
                }
                // the root is its own head
                else {
                    dep.setGovernor(token);
                }
                dep.setBegin(dep.getDependent().getBegin());
                dep.setEnd(dep.getDependent().getEnd());
                dep.addToIndexes();
            }
        }
    }

    /**
     * Generates a temporary file from a jcas. This is needed as input to the MST parser.
     *
     * @param jcas
     *            The JCas containing the textual input
     * @return The path to the created temporary file.
     * @throws IOException
     *             The temporary file could not be created
     */
    private String generateTempInputFile(JCas jcas)
        throws IOException
    {
        File tempfile = File.createTempFile("MSTinput", "txt");
        BufferedWriter out = new BufferedWriter(new FileWriter(tempfile, true));

        // write sentences to temporary file in MST input format
        for (Sentence sentence : select(jcas, Sentence.class)) {
            int tokencount = 0;

            for (Token token : selectCovered(jcas, Token.class, sentence)) {
                out.write(token.getCoveredText() + "\t");
                tokencount++;
            }
            out.write("\n");
            for (POS pos : selectCovered(jcas, POS.class, sentence)) {
                out.write(pos.getPosValue() + "\t");

            }
            // Dummy values for labels
            out.write("\n");
            for (int k = 0; k < tokencount; k++) {
                out.write("Dummy\t");
            }
            // Dummy values for heads
            out.write("\n");
            for (int i = 0; i < tokencount; i++) {
                out.write("0\t");
            }

            out.write("\n\n");
        }

        IOUtils.closeQuietly(out);
        tempfile.deleteOnExit();
        return tempfile.getPath();
    }
}