#include "HuffmanNode.h"
#include "llama.h"

HuffmanNode::HuffmanNode(
    llama_token token,
    float logit,
    HuffmanNode* left,
    HuffmanNode* right
)
: token(token), logit(logit), left(left), right(right)
{ }
