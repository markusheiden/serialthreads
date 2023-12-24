package org.serialthreads.agent;

import org.serialthreads.transformer.ITransformer;

/**
 * Pendant to {@link Transform}.
 *
 * @param transformer Transformer class.
 * @param classPrefixes Prefixes of classes to transform. "org.serialthreads." will always be added.
 */
public record TransformAnnotation(Class<? extends ITransformer> transformer, String[] classPrefixes) {
}
