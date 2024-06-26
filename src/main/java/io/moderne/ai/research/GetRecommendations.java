/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.ai.research;

import io.moderne.ai.AgentGenerativeModelClient;
import io.moderne.ai.table.Recommendations;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


@Value
@EqualsAndHashCode(callSuper = false)
public class GetRecommendations extends Recipe {


    @Option(displayName = "random sampling",
            description = "Do random sampling or use clusters based on embeddings to sample.")
    Boolean random_sampling;

    String path = "/app/methodsToSample.txt" ;

    transient Recommendations recommendations_table = new Recommendations(this);
    private static final Random random = new Random(13);
    @Override
    public String getDisplayName() {
        return "Get recommendations";
    }

    @Override
    public String getDescription() {
        return "This recipe calls an AI model to get recommendations for modernizing" +
               " the code base by looking at a sample of method declarations.";
    }
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        if (!random_sampling){
            AgentGenerativeModelClient.populateMethodsToSample(path);}

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                Boolean isMethodToSample = false;
                JavaSourceFile javaSourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                String source = javaSourceFile.getSourcePath().toString();
                if (random_sampling){
                    isMethodToSample = random.nextInt(200) <= 1;
                }
                else{
                    //TODO: right now only method per file due to hashmap <String, String>... could be more than one!
                    HashMap<String, String> methodsToSample = AgentGenerativeModelClient.getMethodsToSample();
                    isMethodToSample = methodsToSample.get(source) != null && methodsToSample.get(source).equals(md.getSimpleName());
                }
                if ( isMethodToSample ) { // samples based on the results from running GetCodeEmbedding and clustering
                    long time = System.nanoTime();
                    // Get recommendations
                    ArrayList<String> recommendations;
                    recommendations = AgentGenerativeModelClient.getInstance().getRecommendations(md.printTrimmed(getCursor()));

                    List<String> recommendationsQuoted = recommendations.stream()
                            .map(element -> "\"" + element + "\"")
                            .collect(Collectors.toList());
                    String recommendationsAsString = "[" + String.join(", ", recommendationsQuoted) + "]";

                    int tokenSize = (int) ((md.printTrimmed(getCursor())).length()/3.5 + recommendations.toString().length()/3.5 ) ;
                    double elapsedTime = (System.nanoTime()-time)/1e9;

                    recommendations_table.insertRow(ctx, new Recommendations.Row(md.getSimpleName(),
                            elapsedTime,
                            tokenSize,
                            recommendationsAsString));
                }
                return md;
            }
        };


        }


}

