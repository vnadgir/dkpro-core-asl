/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.io.conll;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.io.JCasFileWriter_ImplBase;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

/**
 * Writes a specific Conll File (9 TAB separated) annotation from the CAS object. Example of output
 * file:
 * 
 * <pre>
 * Heutzutage heutzutage ADV _ _ ADV _ _
 * </pre>
 * <ol>
 * <li>ID - token number in sentence</li>
 * <li>FORM - token</li>
 * <li>LEMMA - lemma</li>
 * <li>CPOSTAG - part-of-speech tag (coarse grained)</li>
 * <li>POSTAG - part-of-speech tag</li>
 * <li>FEATS - unused</li>
 * <li>HEAD - target token for a dependency parsing</li>
 * <li>DEPREL - function of the dependency parsing</li>
 * <li>PHEAD - unused</li>
 * <li>PDEPREL - unused</li>
 * </ol>
 * 
 * Sentences are separated by a blank new line
 * 
 * @author Seid Muhie Yimam
 * @author Richard Eckart de Castilho
 * 
 * @see <a href="http://ilk.uvt.nl/conll/">CoNLL-X Shared Task: Multi-lingual Dependency Parsing</a>
 */
@TypeCapability(inputs = { "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
        "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.morph.Morpheme",
        "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma",
        "de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency" })
public class Conll2006Writer
    extends JCasFileWriter_ImplBase
{
    private static final String UNUSED = "_";

    /**
     * Name of configuration parameter that contains the character encoding used by the input files.
     */
    public static final String PARAM_ENCODING = ComponentParameters.PARAM_SOURCE_ENCODING;
    @ConfigurationParameter(name = PARAM_ENCODING, mandatory = true, defaultValue = "UTF-8")
    private String encoding;

    public static final String PARAM_FILENAME_SUFFIX = "filenameSuffix";
    @ConfigurationParameter(name = PARAM_FILENAME_SUFFIX, mandatory = true, defaultValue = ".conll")
    private String filenameSuffix;

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new OutputStreamWriter(getOutputStream(aJCas, filenameSuffix),
                    encoding));
            convert(aJCas, out);
        }
        catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
        finally {
            closeQuietly(out);
        }
    }

    private void convert(JCas aJCas, PrintWriter aOut)
    {
        for (Sentence sentence : select(aJCas, Sentence.class)) {
            HashMap<Token, Row> ctokens = new LinkedHashMap<Token, Row>();

            // Tokens
            Iterator<Token> tokens = selectCovered(Token.class, sentence).iterator();
            for (int i = 1; tokens.hasNext(); i++) {
                Row row = new Row();
                row.id = i;
                row.token = tokens.next();
                ctokens.put(row.token, row);
            }

            // Dependencies
            for (Dependency rel : selectCovered(Dependency.class, sentence)) {
                ctokens.get(rel.getDependent()).deprel = rel;
            }

            // Write sentence in CONLL 2006 format
            for (Row row : ctokens.values()) {
                String lemma = (row.token.getLemma() != null) ? row.token.getLemma().getValue()
                        : UNUSED;

                String pos = UNUSED;
                String cpos = UNUSED;
                if (row.token.getPos() != null) {
                    POS posAnno = row.token.getPos();
                    pos = posAnno.getPosValue();
                    if (!(posAnno instanceof POS)) {
                        cpos = posAnno.getClass().getSimpleName();
                    }
                    else {
                        cpos = pos;
                    }
                }
                
                int head = 0;
                String deprel = UNUSED;
                if (row.deprel != null) {
                    deprel = row.deprel.getDependencyType();
                    head = ctokens.get(row.deprel.getGovernor()).id;
                    if (head == row.id) {
                        // ROOT dependencies may be modeled as a loop, ignore these.
                        head = 0;
                    }
                }

                aOut.printf("%d\t%s\t%s\t%s\t%s\t_\t%d\t%s\t_\t_\n", row.id,
                        row.token.getCoveredText(), lemma, cpos, pos, head, deprel);
            }

            aOut.println();
        }
    }

    private static final class Row
    {
        int id;
        Token token;
        Dependency deprel;
    }
}
