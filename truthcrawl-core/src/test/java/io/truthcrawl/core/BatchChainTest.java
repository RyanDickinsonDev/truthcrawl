package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchChainTest {

    private static final String ROOT_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOT_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ROOT_C =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String HASH =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

    private ChainLink link(String batchId, String merkleRoot, String previousRoot) {
        return new ChainLink(batchId, merkleRoot, HASH, 1, previousRoot);
    }

    @Test
    void valid_chain_of_three() {
        ChainLink g = link("2024-01-01", ROOT_A, ChainLink.GENESIS_ROOT);
        ChainLink l1 = link("2024-01-02", ROOT_B, ROOT_A);
        ChainLink l2 = link("2024-01-03", ROOT_C, ROOT_B);

        BatchChain chain = BatchChain.of(List.of(g, l1, l2));
        assertEquals(3, chain.length());
        assertEquals(g, chain.genesis());
        assertEquals(l2, chain.head());
    }

    @Test
    void single_genesis_link_is_valid() {
        ChainLink g = link("2024-01-01", ROOT_A, ChainLink.GENESIS_ROOT);
        BatchChain chain = BatchChain.of(List.of(g));
        assertEquals(1, chain.length());
        assertEquals(g, chain.genesis());
        assertEquals(g, chain.head());
    }

    @Test
    void rejects_empty_list() {
        assertThrows(IllegalArgumentException.class, () ->
                BatchChain.of(List.of()));
    }

    @Test
    void rejects_non_genesis_first_link() {
        ChainLink bad = link("2024-01-01", ROOT_A, ROOT_B);
        assertThrows(IllegalArgumentException.class, () ->
                BatchChain.of(List.of(bad)));
    }

    @Test
    void rejects_broken_chain() {
        ChainLink g = link("2024-01-01", ROOT_A, ChainLink.GENESIS_ROOT);
        // l1's previous_root should be ROOT_A but is ROOT_C
        ChainLink l1 = link("2024-01-02", ROOT_B, ROOT_C);
        assertThrows(IllegalArgumentException.class, () ->
                BatchChain.of(List.of(g, l1)));
    }

    @Test
    void links_are_unmodifiable() {
        ChainLink g = link("2024-01-01", ROOT_A, ChainLink.GENESIS_ROOT);
        BatchChain chain = BatchChain.of(List.of(g));
        assertThrows(UnsupportedOperationException.class, () ->
                chain.links().clear());
    }
}
