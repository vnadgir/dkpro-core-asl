/*******************************************************************************
 * Copyright 2010
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
package de.tudarmstadt.ukp.dkpro.core.io.bincas;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.uimafit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;
import static org.uimafit.pipeline.SimplePipeline.runPipeline;

import java.io.File;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.util.CasCreationUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;

import de.tudarmstadt.ukp.dkpro.core.api.io.ResourceCollectionReaderBase;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;

public class SerializedCasWriterReaderTest
{
	@Rule
	public TemporaryFolder testFolder = new TemporaryFolder();

	@Test
	public void test() throws Exception
	{
		write();
		read();
	}

	public void write() throws Exception
	{
		CollectionReader textReader = CollectionReaderFactory.createCollectionReader(
				TextReader.class,
				ResourceCollectionReaderBase.PARAM_PATH, "src/test/resources/texts",
				ResourceCollectionReaderBase.PARAM_PATTERNS, new String [] {
					ResourceCollectionReaderBase.INCLUDE_PREFIX+"*.txt"
				},
				ResourceCollectionReaderBase.PARAM_LANGUAGE, "latin");

		AnalysisEngine xmiWriter = AnalysisEngineFactory.createPrimitive(
				SerializedCasWriter.class,
				SerializedCasWriter.PARAM_PATH, testFolder.getRoot().getPath());

		runPipeline(textReader, xmiWriter);

		assertTrue(new File(testFolder.getRoot(), "example1.txt.ser").exists());
	}

	public void read() throws Exception
	{
		CollectionReader xmiReader = CollectionReaderFactory.createCollectionReader(
				SerializedCasReader.class,
				ResourceCollectionReaderBase.PARAM_PATH, testFolder.getRoot().getPath(),
				ResourceCollectionReaderBase.PARAM_PATTERNS, new String [] {
					ResourceCollectionReaderBase.INCLUDE_PREFIX+"*.ser"
				});

		CAS cas = CasCreationUtils.createCas(createTypeSystemDescription(), null, null);
		xmiReader.getNext(cas);

		String refText = readFileToString(new File("src/test/resources/texts/example1.txt"));
		assertEquals(refText, cas.getDocumentText());
		assertEquals("latin", cas.getDocumentLanguage());
	}
}
