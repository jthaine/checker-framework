package org.checkerframework.checker.lock.qual;

import java.lang.annotation.*;

import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.DefaultLocation;
import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.InvisibleQualifier;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeQualifier;

import com.sun.source.tree.Tree;

/**
 * The bottom of the guarded-by qualifier hierarchy.
 * <p>
 *
 * This annotation may not be written in source code; it is an
 * implementation detail of the checker.
 *
 * @checker_framework.manual #lock-checker Lock Checker
 */
@TypeQualifier
@InvisibleQualifier
@SubtypeOf(GuardedBy.class)
@Documented
@DefaultFor({DefaultLocation.LOWER_BOUNDS})
@Target({}) // not necessary to be used by the programmer
@Retention(RetentionPolicy.RUNTIME)
public @interface GuardedByBottom {}