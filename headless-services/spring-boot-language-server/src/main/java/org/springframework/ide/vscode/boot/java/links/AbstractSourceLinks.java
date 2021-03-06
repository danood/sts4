/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.links;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.TypeUrlProviderFromContainerUrl;
import org.springframework.ide.vscode.commons.util.text.Region;

/**
 * Base logic for {@link SourceLinks} independent of any client
 *
 * @author Alex Boyko
 *
 */
public abstract class AbstractSourceLinks implements SourceLinks {

	private static final Logger log = LoggerFactory.getLogger(AbstractSourceLinks.class);

	private CompilationUnitCache cuCache;

	protected AbstractSourceLinks(CompilationUnitCache cuCache) {
		this.cuCache = cuCache;
	}

	@Override
	public Optional<String> sourceLinkUrlForFQName(IJavaProject project, String fqName) {
		Optional<File> classpathResource = project.getIndex().findClasspathResourceContainer(fqName);
		if (classpathResource.isPresent()) {
			File file = classpathResource.get();
			if (file.isDirectory()) {
				return javaSourceLinkUrl(project, fqName, file);
			} else {
				return jarSourceLinkUrl(project, fqName, file);
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<String> sourceLinkUrlForClasspathResource(IJavaProject project, String path) {
		int idx = path.lastIndexOf(CLASS);
		if (idx >= 0) {
			Path p = Paths.get(path.substring(0, idx));
			return sourceLinkUrlForFQName(project, p.toString().replace(File.separator, "."));
		}
		return Optional.empty();
	}



	private Optional<String> javaSourceLinkUrl(IJavaProject project, String fqName, File containerFolder) {
		IClasspath classpath = project.getClasspath();
		return SourceLinks.sourceFromSourceFolder(fqName, classpath)
			.map(sourcePath -> javaSourceLinkUrl(project, sourcePath, fqName));
	}

	private String javaSourceLinkUrl(IJavaProject project, Path sourcePath, String fqName) {
		Optional<String> linkOptional = sourceLinkForResourcePath(sourcePath);
		if (linkOptional.isPresent()) {
			Optional<String> positionLink = findCU(project, sourcePath.toUri()).map(cu -> positionLink(cu, fqName));
			return positionLink.isPresent() ? linkOptional.get() + positionLink.get() : linkOptional.get();
		}
		return null;
	}

	abstract protected String positionLink(CompilationUnit cu, String fqName);

	private Optional<CompilationUnit> findCU(IJavaProject project, URI uri) {
		return cuCache == null ? Optional.empty() : cuCache.withCompilationUnit(project, uri, compilationUnit -> Optional.ofNullable(compilationUnit));
	}

	abstract protected Optional<String> jarLinkUrl(IJavaProject project, String fqName, File jarFile);

	private Optional<String> jarSourceLinkUrl(IJavaProject project, String fqName, File jarFile) {
		return jarLinkUrl(project, fqName, jarFile).map(sourceUrl -> {
			Optional<String> positionLink = findCUForFQNameFromJar(project, jarFile, fqName).map(cu -> positionLink(cu, fqName));
			return positionLink.isPresent() ? sourceUrl + positionLink.get() : sourceUrl;
		});
	}

	private Optional<CompilationUnit> findCUForFQNameFromJar(IJavaProject project, File jarFile, String fqName) {
		return project.sourceContainer(jarFile).map(url -> {
			try {
				return TypeUrlProviderFromContainerUrl.JAR_SOURCE_URL_PROVIDER.url(url, fqName);
			} catch (Exception e) {
				log.warn("Failed to determine source URL from url={} fqName={}", url, fqName, e);
				return null;
			}
		}).map(sourceUrl -> {
			try {
				return sourceUrl.toURI();
			} catch (URISyntaxException e) {
				throw new IllegalStateException(e);
			}
		}).map(sourcePath -> findCU(project, sourcePath).orElse(null));
	}

	protected Region findTypeRegion(CompilationUnit cu, String fqName) {
		if (cu == null) {
			return null;
		}
		int[] values = new int[] {0, -1};
		int lastDotIndex = fqName.lastIndexOf('.');
		String packageName = fqName.substring(0, lastDotIndex);
		String typeName = fqName.substring(lastDotIndex + 1);
		if (packageName.equals(cu.getPackage().getName().getFullyQualifiedName())) {
			Stack<String> visitedType = new Stack<>();
			cu.accept(new ASTVisitor() {

				private boolean visitDeclaration(AbstractTypeDeclaration node) {
					visitedType.push(node.getName().getIdentifier());
					if (values[1] < 0) {
						if (String.join("$", visitedType.toArray(new String[visitedType.size()])).equals(typeName)) {
							values[0] = node.getName().getStartPosition();
							values[1] = node.getName().getLength();
						}
					}
					return values[1] < 0;
				}

				@Override
				public boolean visit(TypeDeclaration node) {
					return visitDeclaration(node);
				}

				@Override
				public boolean visit(AnnotationTypeDeclaration node) {
					return visitDeclaration(node);
				}

				@Override
				public boolean visit(EnumDeclaration node) {
					return visitDeclaration(node);
				}

				@Override
				public void endVisit(EnumDeclaration node) {
					visitedType.pop();
					super.endVisit(node);
				}

				@Override
				public void endVisit(AnnotationTypeDeclaration node) {
					visitedType.pop();
					super.endVisit(node);
				}

				@Override
				public void endVisit(TypeDeclaration node) {
					visitedType.pop();
					super.endVisit(node);
				}

			});
		}
		return values[1] < 0 ? null : new Region(values[0], values[1]);
	}

}
