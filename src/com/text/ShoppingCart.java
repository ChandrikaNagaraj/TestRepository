/*
 * ===========================================================================
 * |      Copyright (c) 2000 Oracle Corporation, Redwood Shores, CA, USA
 * |                         All rights reserved.
 * +=========================================================================
 ==
 * |  HISTORY
 * |       10-Mar-2001  Vikram Kolar  Created.
 * |
 * |       26-AUG-2002  nsultan   Enh. bug #2503762:Implemented quote 
 * |                              Access Validation
 * |       04-Apr-2003  Knachiap  Enh# 2848822 - Store/Load MinisiteId in QuoteHeader
 * |       12-May-2003  Knachiap  Bug#2931358&2931367 Perf- define size 
 * |                               in RegisterOutParam & defineColumnType
 * |       21-May-2003  Knachiap  Bug#2931358&2931367 Perf- Used static size 
 * |                              for x_return_status & x_return_msg
 * |       09-Jul-03    nsultan   bug# 3037438 unable to add config  item 
 * |                              from the directitementry page
 * |       31-Jul-2003  makulkar  11.5.10 - Update Published Quote changes
 * |       01-Aug-03    Knachiap  3047488 - Service Item PhaseII Enhancement
 * |       06-Aug-2003  nsultan   11.5.10 - DFF implementation new variables added.
 * |       03-Sep-2003  Knachiap  11.5.10 TCA V2 UPTAKE IN SHOPPINGCART & CHECKOUT
 * |       09-Sep-2003  nsultan   bug# 2771509 cookie cleanup for load failures-loadandfill()
 * |       10-Sep-2003  Knachaip  3092038 - Service Item Phase II Issue Fix
 * |       04-Nov-2003  Knachiap  3138323 Unpublished item fixes 
 * |       12-Nov-2003  Knachiap  3192506 IBE_ORDER_AUTH_PMT_OFFLINE  Profile Obsoletion
 * |       11-Dec-2003  Knachiap  3259229  Obsoleted IBE_PRICE_REQUEST_TYPE 
 * |                                        & IBE_INCARTLINE_PRICING_EVENT Profiles
 * |       08-Jan-2004  Knachiap 3310976 Fix for CONCURRENCY ISSUES in RSA page
 * |       09-Jan-2004  Knachiap 3310976 PL/SQL Error Message is thrown 
 * |       10/Feb/2004  Knachiap 3322459 - Disabled Item fix
 * |       10-Dec-2004  gzhang 4041867 - allow ordering empty model item
 * |       10-Dec-2004  gzhang 4469168 - backport request for bug#4369007
 * |    16/Sep/05 NSULTAN 4616269 - CC Encryptions
 * +===========================================================================
 */

package oracle.apps.ibe.shoppingcart.quote;

/*import javax.servlet.http.HttpServletRequest;*/
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.text.ParseException;
import com.sun.java.util.collections.ArrayList;
import com.sun.java.util.collections.HashMap;
import oracle.sql.ARRAY;
import oracle.jdbc.driver.OracleCallableStatement;
import oracle.jdbc.driver.OraclePreparedStatement;
import oracle.jdbc.driver.OracleConnection;
import oracle.jdbc.driver.OracleTypes;
import oracle.jdbc.driver.OracleResultSet;
import oracle.apps.fnd.common.VersionInfo;
import oracle.apps.fnd.util.dateFormat.OracleDateFormat;
import oracle.apps.fnd.common.LookupObject;
import oracle.apps.jtf.aom.transaction.TransactionScope;
import oracle.apps.jtf.base.resources.FrameworkException;
import oracle.apps.jtf.util.FndConstant;
import oracle.apps.jtf.util.GeneralUtil;
import oracle.apps.jtf.util.ErrorStackUtil;
import oracle.apps.ibe.shoppingcart.util.ShoppingCartUtil;
import oracle.apps.ibe.shoppingcart.util.QuoteStatus;
import oracle.apps.ibe.shoppingcart.util.QuoteUtil;
import oracle.apps.ibe.shoppingcart.util.PriceList;
import oracle.apps.ibe.util.RequestCtx;
import oracle.apps.ibe.util.IBEUtil;
import oracle.apps.ibe.customer.CCPayment;
import oracle.apps.ibe.store.StoreMinisite;
import oracle.apps.ibe.catalog.Item;
import oracle.apps.ibe.catalog.PriceObject;
import oracle.apps.ibe.catalog.ItemNotFoundException;
import oracle.apps.ibe.catalog.CatalogException;
import oracle.apps.ibe.displaymanager.DisplayManager;
import oracle.apps.ibe.displaymanager.MediaException;
import oracle.apps.ibe.tcav2.Address;
import oracle.apps.ibe.tcav2.AddressManager;
import oracle.apps.ibe.tcav2.PartyManager;
import oracle.apps.ibe.tcav2.ContactManager;
import oracle.apps.ibe.tcav2.Email;
import oracle.apps.ibe.tcav2.Phone;
import oracle.apps.ibe.util.NumFormat;

import oracle.apps.jtf.base.interfaces.*;
 import oracle.apps.jtf.base.interfaces.MessageManagerInter;
 import oracle.apps.jtf.base.resources.*;

/**
 * This object provides methods to retrieve and update a shopping cart. Among the methods are
 * 'set' and 'load' methods to retrieve various properties of the cart and 'get' and 'save' methods to update the properties of the cart.
 * Also, there are several methods to retrieve pricing information of the cart. The returned prices are formatted
 * according to the user's currency preference, e.x. $10.00.
 * Some of the 'save' operations are initiated by setting the shopping cart items
 * to be modified by using the 'setShoppingCartItems' method.
 * Most 'save' operations need to be initiated by calling the ShoppingCart
 * constructor which accepts the unique identifier of the cart and the last modified timestamp of the cart.
 * @rep:scope public
 * @rep:displayname Shopping Cart
 * @rep:product IBE
 * @rep:category BUSINESS_ENTITY IBE_SHOPPING_CART
 */
public class ShoppingCart extends Quote
{
  private static final String     CLASS =
    "ibe:ShoppingCart";
  public static final String      YES = "Y";
  public static final String      NO = "N";
  public static final String      EMPTY_STRING = "";
  public static final String      SAVE_NEW = "NEW";
  public static final String      SAVE_APPEND = "APPEND";
  public static final String      SAVE_REPLACE = "REPLACE";
  public static final String      PO_PAYMENT = "PO";
  public static final String      CC_PAYMENT = "CREDIT_CARD";
  public static final String      INVOICE_PAYMENT = "INVOICE";
  public static final String      FAX_CC_PAYMENT = "FAX_CC";
  public static final String      FAX_PO_PAYMENT = "FAX_PO";
  public static final String      CHECK_PAYMENT = "CHECK";
  public static final String      CASH_PAYMENT = "CASH";
  public static final String      UPDATE_OPCODE = "UPDATE";
  public static final String      CREATE_OPCODE = "CREATE";
  public static final String      DELETE_OPCODE = "DELETE";
  public static final String      ACCOUNT_USER_TYPE = "IStore Account";
  public static final String      WALKIN_USER_TYPE = "IStore Walkin";

  private static final String     UPDATE_SERVICE_ONLY = "SERVICE_ONLY";
  private static final String     UPDATE_QUANTITY_AND_SERVICE = "QUANTITY_AND_SERVICE";

  private static final String     asoApplicationName = "ASO";
  private static final String     flexfieldName = "ASO_HEADER_ATTRIBUTES";
  public static final BigDecimal  gMissNum = FndConstant.getGMissNum();
  public static final String      gMissNumStr = gMissNum.toString();
  public static final String      gMissChar = FndConstant.getGMissChar();
  public static final Timestamp   gMissDate = FndConstant.getGMissDate();
  public static final String      strgMissNum = gMissNum.toString();
  /** Operation code to represent */
  public static final String      NOOP_STRING = FndConstant.getGMissChar();
  
  private static final int        BUNDLE_CHILDREN = 1;
  private static final int        CONFIG_CHILDREN = 2;

  public static final int LOAD_CART = 0;
  public static final int LOAD_QUOTE = 1;

  public static final int SHIP_INFO = 0;
  public static final int BILL_INFO = 1;

  public static final int ADDTO_ACTIVE_CART   = 0; // send "SAVE_ADDTOCART" as save_type to plsql
  public static final int ADDTO_EXPRESS_ORDER = 1; // send "SAVE_EXPRESSORDER" as save_type to plsql
  public static final int UPDATE_EXPRESS_ORDER = 2; // send "UPDATE_EXPRESSORDER" as save_type to plsql

  public static final String      RCS_ID =
    "$Header: ShoppingCart.java 115.152.115100.9 2005/09/16 17:11:42 nsultan ship $";

  public static final boolean     RCS_ID_RECORDED =
    VersionInfo.recordClassVersion(RCS_ID, 
                                   "oracle.apps.ibe.shoppingcart.quote");

  protected String                cartId = "";
  private String                  cartNumber = "";
  protected String                cartName = "";
  private String                  versionNumber = "";
  public  String                  latestVersionNumber = "";
  private String                  creationDate = "";
  private String                  lastModifiedDate = "";
  protected String                lastModifiedTimestamp = "";
  private String                  expirationDate = "";
  private String                  expirationTimestamp = "";
  private String                  status = "";
  private ShoppingCartItem[]      shopCartItems = null;
  private ShoppingCartItem[]      shopCartPRGItems = null; // for loading purposes only, not intended for save api's
  public ShoppingCart[]           versions = null;
  private String                  customerName = "";
  private String                  customerId = "";
  private String                  customerAccountId = "";
  private String                  customerPartyId = "";
  private String                  currencyCode = "";
  private String                  totalListPrice = "";
  private String                  shippingAndHandling = "";
  private String                  tax = "";
  private String                  subTotalPrice = "";
  private String                  totalPrice = "";
  private String                  totalDiscounts = "";
  public String                   supportLevel = "";
  private String                  shipmentId = "";
  private String                  soldtoContactPartyId = "";
  private String                  soldtoContactName = "";
  private String                  soldtoContactEmail = "";
  private String                  soldtoContactPhone = "";
  private String                  shiptoCustomerAccountId = "";
  private String                  shiptoCustomerName = "";
  private String                  shiptoCustomerPartyType = "";
  private String                  shiptoCustomerPartyId = "";
  private String                  shiptoContactPartyId = "";
  private String                  shiptoContactName = "";
  private String                  shiptoContactPhone = "";
  private String                  shiptoContactEmail = "";
  private String                  shiptoPartySiteId = "";
  private String                  shiptoPartySiteType = "";
  private String                  shippingMethod = "";
  private String                  shippingMethodDescription = "";
  private String                  shippingInstructions = "";
  private String                  packingInstructions = "";
  private String                  requestedDeliveryDate = "";
  private String                  paymentId = "";
  private String                  billtoCustomerAccountId = "";
  private String                  billtoCustomerName = "";
  private String                  billtoCustomerPartyType = "";
  private String                  billtoCustomerPartyId = "";
  private String                  billtoContactPartyId = "";
  private String                  billtoContactName = "";
  private String                  billtoContactPhone = "";
  private String                  billtoContactEmail = "";
  private String                  billtoPartySiteId = "";
  private String                  billtoPartySiteType = "";
  private CCPayment               cc = null;
  private String                  ccExpiryTimestamp = "";
  private String                  ccHolderName = "";
  private String                  ccTypeCode = "";
  private String                  paymentType = "";
  private String                  paymentNumber = "";
  private String                  taxDetailId = "";
  private String                  taxExemptFlag = "";
  private String                  taxReasonCode = "";
  private String                  taxCertificateNumber = "";
  private String                  poNumber = "";
  private String                  orderNumber = "";
  public CCTrxnOutRecord          ccTrxnOutRecord = null;
  public OrderHeaderRecord        orderHeaderRec = null;
  private boolean                 summaryLoaded = false;
  public boolean                  containsSVAFlag = false;
  private boolean                 soldtoInfoLoaded = false;
  private boolean                 headerLoaded = false;
  private boolean                 isPublished = false;
  private boolean                 reloadFlag = false;
  protected boolean               isOrderable = true;
  private TaxInfo[]               taxInfo = null;
  private boolean                 containsUnorderableItems = false;
  private Contract                associatedContract;
  private Agreement               agreementInfo = null;
  protected CartLoadControlRecord   loadControlRec = null;
  // template ids added for bug 2411012
  private String                  contract_template_id = "";
  private String                  contract_template_major_ver = "";
  private String                  minisiteId = "";
  private String                  ccHashCode1  = "";
  private String                  ccHashCode2  = "";

  private boolean                 isShared = false;
  private int                     addToCartContext = ADDTO_ACTIVE_CART;
  private int                     saveType = 0;
  private boolean                 containsPRGItems = false;
  protected HashMap               qualifyingItemIdsForPRG = null;
  /**
   * Constructor declaration
   */
  public ShoppingCart()
  {
    super();
  }

  /**
   * Convenience constructor for save operations
   * @param cartId - the id of cart to be saved
   * @param lastModifiedTimestamp - the current time stamp of the cart
   * 
   */
  public ShoppingCart(String cartId, String lastModifiedTimestamp)
  {
    this.cartId = cartId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
  }

  /**
   * Convenience constructor for save operations
   * @param cartId - the id of cart to be saved
   * @param lastModifiedTimestamp - the current time stamp of the cart
   * @param expirationTimestamp - the current time stamp of the cart
   */
  public ShoppingCart(String cartId, String lastModifiedTimestamp,
                      String expirationTimestamp)
  {
    this.cartId = cartId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
    this.expirationTimestamp = expirationTimestamp;
  }


  /**
   * Convenience constructor for shipping details save operations
   * 
   * @param cartId - the id of cart to be saved
   * @param lastModifiedTimestamp - the current time stamp of the cart
   * @param headerShiptoCustomerAccountId - the ship to customer account id at the header level
   * @param headerShiptoContactPartyId - the ship to contact party id at the header level
   * @param headerShiptoPartySiteId - the ship to party site id at the header level
   * @param headerShiptoPartySiteType - the ship to party site type at the header level
   * @param scartItems - the array of shop cart items which contain line level information
   *
   */
  public ShoppingCart(String cartId, String lastModifiedTimestamp,
                      String headerShiptoCustomerAccountId,
                      String headerShiptoContactPartyId,
                      String headerShiptoPartySiteId,
                      String headerShiptoPartySiteType,
                      ShoppingCartItem[] scartItems)
  {
    this.cartId = cartId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
    this.shiptoCustomerAccountId = headerShiptoCustomerAccountId;
    this.shiptoContactPartyId = headerShiptoContactPartyId;
    this.shiptoPartySiteId = headerShiptoPartySiteId;
    this.shiptoPartySiteType = shiptoPartySiteType;
    this.shopCartItems = scartItems;
  }
/*
  public ShoppingCart(String cartId, String lastModifiedTimestamp,
                      String headerShiptoCustomerAccountId,
                      String headerShiptoContactPartyId,
                      String headerShiptoPartySiteId,
                      String headerShiptoPartySiteType,
                      ShoppingCartItem[] scartItems)
  {
   ShoppingCart(cartId,
                lastModifiedTimestamp,
                headerShiptoCustomerAccountId,
                headerShiptoContactPartyId,
                headerShiptoPartySiteId,
                shiptoPartySiteType,
                scartItems,
                this.SHIP_INFO);
  }
*/

  /**
   * Convenience constructor for shipping or billing save operations
   */
  public ShoppingCart(String cartId, String lastModifiedTimestamp,
                      String headerSBtoCustomerAccountId,
                      String headerSBtoContactPartyId,
                      String headerSBtoPartySiteId,
                      String headerSBtoPartySiteType,
                      ShoppingCartItem[] scartItems,
                      int infoType) // use either ShoppingCart.SHIP_INFO or BILL_INFO
  {
    this.cartId = cartId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
    this.shopCartItems = scartItems;

  if (infoType == SHIP_INFO) {
      this.shiptoCustomerAccountId = headerSBtoCustomerAccountId;
      this.shiptoContactPartyId = headerSBtoContactPartyId;
      this.shiptoPartySiteId = headerSBtoPartySiteId;
      this.shiptoPartySiteType = headerSBtoPartySiteType;
  } else {
      this.billtoCustomerAccountId = headerSBtoCustomerAccountId;
      this.billtoContactPartyId = headerSBtoContactPartyId;
      this.billtoPartySiteId = headerSBtoPartySiteId;
      this.billtoPartySiteType = headerSBtoPartySiteType;
  }
  }

  /**
   * Constructor declaration
   * 
   * 
   * calls super (Quote)
   * 
   */
   // #2 (Base + partyId, accountId)
  private ShoppingCart(BigDecimal cartId, BigDecimal partyId,
                       BigDecimal accountId, boolean loadLine, 
                       boolean loadLineDetail, boolean loadHeaderPriceAttr, 
                       boolean loadLinePriceAttr, boolean loadHeaderPayment, 
                       boolean loadLinePayment, boolean loadHeaderShipment, 
                       boolean loadLineShipment, boolean loadHeaderTaxDetail, 
                       boolean loadLineTaxDetail, boolean loadLineRel, 
                       boolean loadLineAttrExt,
                       boolean includeOrdered) throws SQLException, 
                       FrameworkException, QuoteException
  {
 
    super(cartId, RequestCtx.getPartyId(), RequestCtx.getAccountId(), RequestCtx.getShareeNumber(),
          RequestCtx.getMinisiteId(), loadLine, loadLineDetail, loadHeaderPriceAttr, loadLinePriceAttr, 
          loadHeaderPayment, loadLinePayment, loadHeaderShipment, 
          loadLineShipment, loadHeaderTaxDetail, loadLineTaxDetail, 
          loadLineRel, loadLineAttrExt, includeOrdered, true);
  }

  /**
   * Constructor declaration
   * 
   * calls super (Quote)
   *
   */
   // #1 (Base)
  private ShoppingCart(String cartId, boolean loadLine,
                       boolean loadLineDetail, boolean loadHeaderPriceAttr, 
                       boolean loadLinePriceAttr, boolean loadHeaderPayment, 
                       boolean loadLinePayment, boolean loadHeaderShipment, 
                       boolean loadLineShipment, boolean loadHeaderTaxDetail, 
                       boolean loadLineTaxDetail, boolean loadLineRel, 
                       boolean loadLineAttrExt, 
                       boolean includeOrdered) throws SQLException, 
                       FrameworkException
  {
         super(new BigDecimal(cartId), RequestCtx.getPartyId(), RequestCtx.getAccountId(), 
          RequestCtx.getShareeNumber(), RequestCtx.getMinisiteId(), loadLine, loadLineDetail, 
          loadHeaderPriceAttr, loadLinePriceAttr, loadHeaderPayment,
          loadLinePayment, loadHeaderShipment, loadLineShipment, 
          loadHeaderTaxDetail, loadLineTaxDetail, loadLineRel, 
          loadLineAttrExt, includeOrdered, true);

    /*
     * super(quoteId  ,RequestCtx.getPartyId(), RequestCtx.getAccountId(),
     * loadLine           ,loadLineDetail  ,
     * loadHeaderPriceAttr,loadLinePriceAttr  ,
     * loadHeaderPayment  ,loadLinePayment    ,
     * loadHeaderShipment ,loadLineShipment   ,
     * loadHeaderTaxDetail,loadLineTaxDetail  ,
     * loadLineRel        ,loadLineAttrExt    ,
     * includeOrdered);
     */

  }

  // NEW Signature that deals w/ cartLineID & loadType
  // calls super (Quote)
  // #3 (Base + cartLineId, partyId, accountId, loadType)
  private ShoppingCart(BigDecimal cartId, BigDecimal cartLineId, BigDecimal partyId,
                       BigDecimal accountId, boolean loadLine, 
                       boolean loadLineDetail, boolean loadHeaderPriceAttr, 
                       boolean loadLinePriceAttr, boolean loadHeaderPayment, 
                       boolean loadLinePayment, boolean loadHeaderShipment, 
                       boolean loadLineShipment, boolean loadHeaderTaxDetail, 
                       boolean loadLineTaxDetail, boolean loadLineRel, 
                       boolean loadLineAttrExt, 
                       boolean includeOrdered, int loadType) throws SQLException,
                       FrameworkException, QuoteException
  {
    super(cartId, cartLineId, RequestCtx.getPartyId(), RequestCtx.getAccountId(),
          RequestCtx.getShareeNumber(), RequestCtx.getMinisiteId(), loadLine, loadLineDetail, loadHeaderPriceAttr, loadLinePriceAttr, 
          loadHeaderPayment, loadLinePayment, loadHeaderShipment, 
          loadLineShipment, loadHeaderTaxDetail, loadLineTaxDetail, 
          loadLineRel, loadLineAttrExt, includeOrdered, true, loadType);
  }

  // #5 (New Base - 1st w/ ctrl record)
  private ShoppingCart(BigDecimal cartId, BigDecimal[] cartLineIds, BigDecimal partyId,
                       BigDecimal accountId, BigDecimal retrievalNumber, QuoteLoadControlRecord quoteLoadCtrlRec)
  throws SQLException, FrameworkException, QuoteException
  {
    super(cartId, cartLineIds, partyId, accountId,
           retrievalNumber, RequestCtx.getMinisiteId(), quoteLoadCtrlRec);
  }

  /**
   * Retrieves cart items, except for promotional items.
   *
   *
   * @return An array of {@link oracle.apps.ibe.shoppingcart.quote.ShoppingCartItem ShoppingCartItem} objects
   * @rep:displayname Get Shopping Cart Items
   */
  public ShoppingCartItem[] getShoppingCartItems()
  {
    return shopCartItems;
  }

  /**
   * Returns the PRG shopping cart items in this cart
   * Currently, there is no set method as PRG Items only get loaded.
   *
   * @return ShoppingCartItem[] - an array of PRG shop cart items
   *
   */
  public ShoppingCartItem[] getShopCartPRGItems()
  {
    return shopCartPRGItems;
  }

  /**
   * Sets the shopping cart items for a save operation on the cart
   * 
   * 
   * @param shopCartItems - an array of shopping cart item which contain line
   * level information which needs to be saved
   */
  public void setShoppingCartItems(ShoppingCartItem[] shopCartItems)
  {
    this.shopCartItems = shopCartItems;
  }

  /**
   * Retrieves the unique identifier of the cart.
   * 
   * 
   * @return The unique identifier of the cart.
   * @rep:displayname Get Cart Identifier
   */
  public String getCartId()
  {
    return cartId;
  }

  /**
   * Sets the cart id of this cart for a save operation
   * 
   * 
   * @param cartId - the cart id
   */
  public void setCartId(String cartId)
  {
    this.cartId = cartId;
  }

  /**
   * Retrieves the name of the cart.
   * 
   *
   * @return The name of the cart.
   * @rep:displayname Get Cart Name
   */
  public String getCartName()
  {
    return cartName;
  }

  /**
   * Sets the name of the cart for a save operation.
   * 
   * 
   * @param cartName The name of the cart.
   * @rep:displayname Set Cart Name
   */
  public void setCartName(String cartName)
  {
    this.cartName = cartName;
  }


  /**
   * Retrieves the cart number.
   * 
   * 
   * @return The cart number.
   * @rep:displayname Get Cart Number
   */
  public String getCartNumber()
  {
    return cartNumber;
  }

  /**
   * Sets the cart number of this cart
   * for a save operation.
   * 
   * @param cartNumber - String, the cart number
   */
  public void setCartNumber(String cartNumber)
  {
    this.cartNumber = cartNumber;
  }

  /**
   * Returns the cart version (useful for multiple versions)
   * 
   * 
   * @return String - the cart version number
   */
  public String getVersionNumber()
  {
    return versionNumber;
  }

  /**
   * Sets the cart number of this cart
   * for a save operation.
   *
   * @param versionNumber - String, the cart version number
   */
  public void setVersionNumber(String versionNumber)
  {
    this.versionNumber = versionNumber;
  }

  /**
   * Returns the cart versions
   * This method will return values only when loadWithItemsAndVersions is called.
   * @return ShoppingCart[] - the cart versions
   */

  public ShoppingCart[] getVersions()
  {
    return versions;
  }

  /**
   * Returns the latest cart version number (useful for multiple versions)
   *
   * 
   * @return String - the cart's latest version number
   */
  public String getLatestVersionNumber()
  {
    return latestVersionNumber;
  }

  /**
   * Retrieves the creation date of the cart.
   * 
   * 
   * @return The creation date of the cart.
   * @rep:displayname Get Creation Date
   */
  public String getCreationDate()
  {
    return creationDate;
  }

  /**
   * Retrieves the last modified date of the cart.
   * 
   * @return The last modified date.
   * @rep:displayname Get Last Modified Date
   */
  public String getLastModifiedDate()
  {
    return lastModifiedDate;
  }

  /**
   * Retrieves the last modified date of the cart.
   * 
   * @return The last modified date.
   * @rep:displayname Get Last Modified Date
   */
  public String getLastModifiedTimestamp()
  {
    return lastModifiedTimestamp;
  }

  /**
   * Retrieves the expiration date of the cart.
   * 
   * @return The expiration date.
   * @rep:displayname Get Expiration Date
   */
  public String getExpirationDate()
  {
    return expirationDate;
  }

  /**
   * Retrieves the expiration timestamp of the cart.
   *
   * @return The expiration timestamp of the cart.
   * @rep:displayname Get Expiration Timestamp
   */
  public String getExpirationTimestamp()
  {
    return expirationTimestamp;
  }


  /**
   * Retrieves the status of this cart.
   *
   *
   * @return The status of the cart.
   * @rep:displayname Get Status
   */
  public String getStatus()
  {
    return status;
  }

  /**
   * Retrieves the status code of the cart.
   *
   *
   * return The status code of the cart.
   * rep:displayname Get Status Code
   */
  public String getStatusCode()
  {
    return this.headerRec.quote_status_code;
  }

  /**
   * Retrieves the name of the Sold To customer.
   *
   *
   * @return The name of the Sold To customer.
   * @rep:displayname Get Customer Name
   */
  public String getCustomerName()
  {
    return customerName;
  }

  /**
   * Retrieves the unique identifier of the Sold To customer's account.
   * 
   * 
   * @return The unique identifier of the Sold To customer's account.
   * @rep:displayname Get Customer Identifier
   */
  public String getCustomerId()
  {
    return customerId;
  }

  /**
   * Sets the customer id for this cart for a save operation
   * 
   * 
   * @param customerId - the customer id
   */
  public void setCustomerId(String customerId)
  {
    this.customerId = customerId;
  }

  /**
   * Retrieves the formatted total list price.
   *
   *
   * @return The formatted total list price.
   * @rep:displayname Get Total List Price
   */
  public String getTotalListPrice()
  {
    return totalListPrice;
  }

  /**
   * Retrieves the formatted shipping and handling charges.
   * 
   * 
   * @return The formatted shipping and handling charges.
   * @rep:displayname Get Shipping And Handling
   * 
   */
  public String getShippingAndHandling()
  {
    return shippingAndHandling;
  }

  /**
   * Retrieves the current support level of the cart. The method retrieves:
   * <LI>ShoppingCart.NO_SUPP if no support is set.
   * <LI>ShoppingCart.NONE_SETUP if support is not set up.
   * <LI>ShoppingCart.INVALID_SUPP if the cart contains supports which are invalid.
   * 
   * 
   * @return The current support level.
   * @rep:displayname Get Support Level
   */
  public String getSupportLevel()
  {
    return supportLevel;
  }

  /**
   * Retrieves the formatted tax amount.
   *
   *
   * @return The formatted tax amount.
   * @rep:displayname Get Tax
   */
  public String getTax()
  {
    return tax;
  }

  /**
   * Retrieves the tax details of the cart, including rate, code, amount etc.
   *
   * @return An array of {@link oracle.apps.ibe.shoppingcart.quote.TaxInfo TaxInfo} objects.
   * @rep:displayname Get Tax Information
   */
  public TaxInfo[] getTaxInfo()
  {
    return taxInfo;
  }

  /**
   * Retrieves the unique identifier of the tax detail record associated with the cart.
   *
   * @return The unique identifier of the tax detail record.
   * @rep:displayname Get Tax Detail Identifier
   */
  public String getTaxDetailId()
  {
    return taxDetailId;
  }

  /**
   * Sets the the id of the tax detail record associated with this cart
   * for a save operation.
   * 
   * @param taxDetailId - String, the tax detail record id
   */
  public void setTaxDetailId(String taxDetailId)
  {
    this.taxDetailId = taxDetailId;
  }

  /**
   * Retrieves the formatted subtotal price.
   *
   *
   * @return The formatted subtotal price.
   * @rep:displayname Get Subtotal Price
   */
  public String getSubTotalPrice()
  {
    return subTotalPrice;
  }

  /**
   * Retrieves the formatted total price of the cart.
   *
   *
   * @return The total price.
   * @rep:displayname Get Total Price
   */
  public String getTotalPrice()
  {
    return totalPrice;
  }

  /**
   * Retrieves the formatted total price of the cart (either list or quote price) 
   * based on the control record parameter formatNetPrices.
   *
   *
   * @return The formated total price.
   * @rep:displayname Get Display Cart Price
   */
  public String getDisplayCartPrice()
  {
    //based on control rec instead of permission in order to have a consistent behavior across this object's api's
    if (loadControlRec.formatNetPrices)
      return totalPrice;
    else
      return totalListPrice;
  }

  /**
   * Retrieves the formatted total discounts.
   *
   *
   * @return The formatted total discounts.
   * @rep:displayname Get Total Discounts
   */
  public String getTotalDiscounts()
  {
    return totalDiscounts;
  }

  /**
   * Retrieves the unique identifier of the shipment record associated with the cart.
   *
   * @return The unique identifier of the shipment record, or an empty string if there is
   * no shipment record at the cart level.
   * @rep:displayname Get Shipment Identifier
   */
  public String getShipmentId()
  {
    return shipmentId;
  }

  /**
   * Retrieves the unique identifier of the Sold To customer's account.
   *
   *
   * @return The unique identifier of the Sold To customer's account.
   * @rep:displayname Get Sold To Customer Account Identifier
   */
  public String getSoldToCustomerAccountId()
  {
    return customerAccountId;
  }

  /**
   * Sets the sold to customer's account id. This id is however not
   * saved to the database. It is used for comparison with either
   * ship to customer or bill to customer account ids.
   * @param customerAccountId - String, the sold to customer's account id
   */
  public void setSoldToCustomerAccountId(String soldToCustomerAccountId)
  {
    this.customerAccountId = soldToCustomerAccountId;
  }

  /**
   * Retrieves the unique identifier of the Sold To contact's party.
   *
   *
   * @return The unique identifier of the Sold To contact's party.
   * @rep:displayname Get Sold To Contact Party Identifier
   */
  public String getSoldtoContactPartyId()
  {
    return soldtoContactPartyId;
  }

  /**
   * Sets the sold to contact party id for a save operation
   * 
   * 
   * @param soldtoContactPartyId - String, the sold to contact party id
   */
  public void setSoldtoContactPartyId(String soldtoContactPartyId)
  {
    this.soldtoContactPartyId = soldtoContactPartyId;
  }

  /**
   * Retrieves the name of the Sold To contact.
   *
   *
   * @return The name of the Sold To contact.
   * @rep:displayname Get Sold To Contact Name
   */
  public String getSoldtoContactName()
  {
    return soldtoContactName;
  }

  /**
   * Retrieves the phone number of the Sold To contact.
   *
   *
   * @return The phone number of the Sold To contact.
   * @rep:displayname Get Sold To Contact Phone
   */
  public String getSoldtoContactPhone()
  {
    return soldtoContactPhone;
  }

  /**
   * Retrieves the email address of the Sold To contact.
   *
   *
   * @return The email address of the Sold To contact.
   * @rep:displayname Get Sold To Contact Email address
   */
  public String getSoldtoContactEmail()
  {
    return soldtoContactEmail;
  }

  /**
   * Retrieves the unique identifier of the Ship To customer's account.
   *
   *
   * @return The unique identifier of the Ship To customer's account.
   * @rep:displayname Get Ship To Customer Account Identifier
   */
  public String getShiptoCustomerAccountId()
  {
    return shiptoCustomerAccountId;
  }

  /**
   * Sets the ship to customer id in the cart for a save operation.
   * 
   *
   * @param shiptoCustomerAccountId - String, the ship to customer id
   */
  public void setShiptoCustomerAccountId(String shiptoCustomerAccountId)
  {
    this.shiptoCustomerAccountId = shiptoCustomerAccountId;
  }

  /**
   * Retrieves the name of the Ship To customer.
   *
   *
   * @return The name of the Ship To customer.
   * @rep:displayname Get Ship To Customer Name
   */
  public String getShiptoCustomerName()
  {
    return shiptoCustomerName;
  }

  /**
   * Retrieves the party type of the Ship To customer.
   *
   *
   * @return The party type of the Ship To customer.
   * @rep:displayname Get Ship To Customer Party Type
   */
  public String getShiptoCustomerPartyType()
  {
    return shiptoCustomerPartyType;
  }

  /**
   * Retrieves the unique identifier of the Ship To customer's party.
   *
   *
   * @return The unique identifier of the Ship To customer's party.
   * @rep:displayname Get Ship To Customer Party Identifier
   */
  public String getShiptoCustomerPartyId()
  {
    return shiptoCustomerPartyId;
  }

  /**
   * Sets the ship to customer party id for a save operation
   * 
   * 
   * @param shiptoCustomerPartyId - ship to customer party id
   */
  public void setShiptoCustomerPartyId(String shiptoCustomerPartyId)
  {
    this.shiptoCustomerPartyId = shiptoCustomerPartyId;
  }

  /**
   * Returns the ship to contact party id
   * 
   * 
   * @return String - the ship to contact party id
   */
  public String getShiptoContactPartyId()
  {
    return shiptoContactPartyId;
  }

  /**
   * Sets the ship to contact party id for a save operation
   * 
   * 
   * @param shiptoContactPartyId
   */
  public void setShiptoContactPartyId(String shiptoContactPartyId)
  {
    this.shiptoContactPartyId = shiptoContactPartyId;
  }

  /**
   * Retrieves the name of the Ship To contact.
   *
   *
   * @return The name of the Ship To contact.
   * @rep:displayname Get Ship To Contact Name
   */
  public String getShiptoContactName()
  {
    return shiptoContactName;
  }

  /**
   * Retrieves the phone number of the Ship To contact.
   *
   *
   * @return The phone number of the Ship To contact.
   * @rep:displayname Get Ship To Contact Phone
   */
  public String getShiptoContactPhone()
  {
    return shiptoContactPhone;
  }

  /**
   * Retrieves the email address of the Ship To contact.
   *
   *
   * @return The email address of the Ship To contact.
   * @rep:displayname Get Ship To Contact Email Address
   */
  public String getShiptoContactEmail()
  {
    return shiptoContactEmail;
  }

  /**
   * Retrieves the unique identifier of the Ship To party site.
   *
   *
   * @return The unique identifier of the Ship To party site. 
   * @rep:displayname Get Ship To Party Site Identifier
   */
  public String getShiptoPartySiteId()
  {
    return shiptoPartySiteId;
  }

  /**
   * Sets the ship to address id for a save operation
   * 
   * 
   * @param shiptoPartySiteId - the ship to address id
   */
  public void setShiptoPartySiteId(String shiptoPartySiteId)
  {
    this.shiptoPartySiteId = shiptoPartySiteId;
  }

  /**
   * Retrieves the Ship To party site type.
   *
   *
   * @return The Ship To party site type.
   * @rep:displayname Get Ship To Party Site Type
   */
  public String getShiptoPartySiteType()
  {
    return shiptoPartySiteType;
  }

  /**
   * Retrieves the shipping method code.
   *
   *
   * @return The shipping method code.
   * @rep:displayname Get Shipping Method
   */
  public String getShippingMethod()
  {
    return shippingMethod;
  }

  /**
   * Retrieves the shipping method description.
   *
   *
   * @return The shipping method description.
   * @rep:displayname Get Shipping Method Description
   */
  public String getShippingMethodDescription()
  {
    return shippingMethodDescription;
  }

  /**
   * Sets the shipping method code for a save operation
   * 
   * 
   * @param shippingMethod The shipping method code
   * @rep:displayname Set Shipping Method
   */
  public void setShippingMethod(String shippingMethod)
  {
    this.shippingMethod = shippingMethod;
  }

  /**
   * Retrieves the shipping instructions.
   *
   *
   * @return The shipping instructions.
   * @rep:displayname Get Shipping Instructions
   */
  public String getShippingInstructions()
  {
    return shippingInstructions;
  }

  /**
   * Sets the shipping instructions for a save operation.
   * 
   * 
   * @param shippingInstructions The shipping instructions.
   * @rep:displayname Set Shipping Instructions
   */
  public void setShippingInstructions(String shippingInstructions)
  {
    this.shippingInstructions = shippingInstructions;
  }

  /**
   * Retrieves the packing instructions.
   * 
   * 
   * @return The packing instructions.
   * @rep:displayname Get Packing Instructions
   */
  public String getPackingInstructions()
  {
    return packingInstructions;
  }

  /**
   * Sets the packing instructions for a save operation.
   * 
   * @param packingInstructions The packing instructions
   * @rep:displayname Set Packing Instructions
   */
  public void setPackingInstructions(String packingInstructions)
  {
    this.packingInstructions = packingInstructions;
  }

  /**
   * Retrieves the requested delivery date.
   * 
   * 
   * @return The requested delivery date.
   * @rep:displayname Get Requested Delivery Date
   */
  public String getRequestedDeliveryDate()
  {
    return requestedDeliveryDate;
  }

  /**
   * Sets the requested delivery date for the order. The date has to be in the
   * same pattern as the date format preference of the user.
   * 
   * @param requestedDeliveryDate The requested delivery date.
   * @rep:displayname Set Requested Delivery Date
   */
  public void setRequestedDeliveryDate(String requestedDeliveryDate)
  {
    this.requestedDeliveryDate = requestedDeliveryDate;
  }

  /**
   * Retrieves the unique identifier of the payment record associated with the cart.
   *
   *
   * @return The unique identifier of the payment record associated with the cart.
   * @rep:displayname Get Payment Identifier
   */
  public String getPaymentId()
  {
    return paymentId;
  }

  /**
   * Sets the payment record id. This is necessary for all save operations related
   * to saving payment details.
   * 
   * @param paymentId - the payment id
   */
  public void setPaymentId(String paymentId)
  {
    this.paymentId = paymentId;
  }

  /**
   * Retrieves the unique identifier of the Bill To customer's account.
   *
   *
   * @return The unique identifier of the Bill To customer's account.
   * @rep:displayname Get Bill To Customer Account Identifier
   */
  public String getBilltoCustomerAccountId()
  {
    return billtoCustomerAccountId;
  }

  /**
   * Sets the bill to customer account id
   * 
   * 
   * @param billtoCustomerAccountId - the bill to customer account id
   */
  public void setBilltoCustomerAccountId(String billtoCustomerAccountId)
  {
    this.billtoCustomerAccountId = billtoCustomerAccountId;
  }

  /**
   * Retrieves the name of the Bill To customer.
   *
   *
   * @return The name of the Bill To customer.
   * @rep:displayname Get Bill To Customer Name
   */
  public String getBilltoCustomerName()
  {
    return billtoCustomerName;
  }

  /**
   * Retrieves the party type of the Bill To customer.
   *
   *
   * @return The party type of the Bill To customer.
   * @rep:displayname Get Bill To Customer Party Type
   */
  public String getBilltoCustomerPartyType()
  {
    return billtoCustomerPartyType;
  }

  /**
   * Retrieves the unique identifier of the Bill To customer's party.
   *
   *
   * @return The unique identifier of the Bill To customer's party.
   * @rep:displayname Get Bill To Customer Party Identifier
   */
  public String getBilltoCustomerPartyId()
  {
    return billtoCustomerPartyId;
  }

  /**
   * Sets the bill to customer party id
   * 
   * 
   * @param billtoCustomerPartyId - the bill to customer party id
   */
  public void setBilltoCustomerPartyId(String billtoCustomerPartyId)
  {
    this.billtoCustomerPartyId = billtoCustomerPartyId;
  }

  /**
   * Retrieves the unique identifier of the Bill To contact's party.
   * 
   * 
   * @return The unique identifier of the Bill To contact's party.
   * @rep:displayname Get Bill To Contact Party Identifier
   */
  public String getBilltoContactPartyId()
  {
    return billtoContactPartyId;
  }

  /**
   * Sets the bill to contact party id for a save operation
   *
   * 
   * @param billtoContactPartyId - the bill to contact party id
   */
  public void setBilltoContactPartyId(String billtoContactPartyId)
  {
    this.billtoContactPartyId = billtoContactPartyId;
  }

  /**
   * Retrieves the name of the Bill To contact.
   * 
   * 
   * @return The name of the Bill To contact.
   * @rep:displayname Get Bill To Contact Name
   */
  public String getBilltoContactName()
  {
    return billtoContactName;
  }

  /**
   * Retrieves the phone number of the Bill To contact.
   * 
   * 
   * @return The phone number of the Bill To contact.
   * @rep:displayname Get Bill To Contact Phone
   */
  public String getBilltoContactPhone()
  {
    return billtoContactPhone;
  }

  /**
   * Retrieves the email address of the Bill To contact.
   * 
   *
   * @return The email address of the Bill To contact.
   * @rep:displayname Get Bill To Contact Email
   */
  public String getBilltoContactEmail()
  {
    return billtoContactEmail;
  }

  /**
   * Retrieves the unique identifier of the Bill To party site.
   *
   *
   * @return The Bill To party site identifier.
   * @rep:displayname Get Bill To Party Site Identifier
   */
  public String getBilltoPartySiteId()
  {
    return billtoPartySiteId;
  }

  /**
   * Sets the bill to address id for a save operation
   * 
   * 
   * @param billtoPartySiteId - the bill to address id
   */
  public void setBilltoPartySiteId(String billtoPartySiteId)
  {
    this.billtoPartySiteId = billtoPartySiteId;
  }

  /**
   * Returns the bill to party address type
   * 
   * 
   * @return String - the bill to party address type
   */
  public String getBilltoPartySiteType()
  {
    return billtoPartySiteType;
  }

  /**
   * Retrieves the credit card information associated with the cart.
   *
   * @return The {@link oracle.apps.ibe.customer.CCPayment CCPayment} object, or null if the chosen payment option is not credit card.
   * @rep:displayname Get Credit Card
   * 
   */
  public CCPayment getCreditCard()
  {
    return cc;
  }

  /**
   * Retrieves the credit card expiration timestamp if the chosen payment option is
   * credit card.
   *
   * @return The credit card expiration timestamp, or an empty string if the chosen
   * payment option is not credit card.
   * @rep:displayname Get Credit Card Expiry Timestamp
   */
  public String getCCExpiryTimestamp()
  {
    return ccExpiryTimestamp;
  }

  /**
   * Retrieves the credit card holder name if the chosen payment option is
   * credit card.
   *
   * @return The credit card holder name, or an empty string if the chosen 
   * payment option is not credit card.
   * @rep:displayname Get Credit Card Holder Name
   */
  public String getCCHolderName()
  {
    return ccHolderName;
  }

  /**
   * Retrieves the credit card type code if the chosen payment option is
   * credit card.
   * 
   * @return The credit card type code or an empty string if the chosen 
   * payment option is not credit card.
   * @rep:displayname Get Credit Card Type Code
   */
  public String getCCTypeCode()
  {
    return ccTypeCode;
  }

  /**
   * Sets the CCPayment for a save operation, when the payment option is credit card.
   * The cc object will need to contain all the particulars of the cc like number,
   * code, expiry date etc.
   * @param cc - the CCPayment object
   */
  public void setCreditCard(CCPayment cc)
  {
    this.cc = cc;
  }

  /**
   * Retrieves the payment type associated with the cart. The method retrieves:
   * <LI> ShoppingCart.FAX_CC_PAYMENT if the payment option is faxed credit card.
   * <LI> ShoppingCart.FAX_PO_PAYMENT if the payment option is faxed purchase order.
   * <LI> ShoppingCart.CC_PAYMENT if the payment option is credit card.
   * <LI> ShoppingCart.INVOICE_PAYMENT if the payment option is invoice.
   * <LI> ShoppingCart.PO_PAYMENT if the payment option is purchase order.
   * 
   * 
   * @return The payment type.
   * @rep:displayname Get Payment Type
   */
  public String getPaymentType()
  {
    return paymentType;
  }

  /**
   * Sets the payment type. The payment type has to be set to one of the following value:
   * <LI> ShoppingCart.FAX_CC_PAYMENT if the payment option is faxed credit card
   * <LI> ShoppingCart.FAX_PO_PAYMENT if the payment option is faxed purchase order
   * <LI> ShoppingCart.CC_PAYMENT if the payment option is credit card
   * <LI> ShoppingCart.INVOICE_PAYMENT if the payment option is invoice
   * <LI> ShoppingCart.PO_PAYMENT if the payment option is purchase order
   * 
   * 
   * @param paymentType The payment type.
   * @rep:displayname Set Payment Type
   */
  public void setPaymentType(String paymentType)
  {
    this.paymentType = paymentType;
  }

  /**
   * Retrieves the payment instrument number. It will be the credit card number if the payment option
   * is credit card, the purchase order number if the option is purchase order. Else,
   * returns an empty string.
   * @return The payment instrument number.
   * @rep:displayname Get Payment Number
   */
  public String getPaymentNumber()
  {
    return paymentNumber;
  }

  /**
   * Sets the payment number for the save operation. If the payment option is CC,
   * then the number is a credit card number. If the payment option is PO, then the
   * the number is the purchase order number.
   * @param paymentNumber - the payment number
   */
  public void setPaymentNumber(String paymentNumber)
  {
    this.paymentNumber = paymentNumber;
  }

  /**
   * Retrieves the HashCode1 of the given credit number. 
   *
   * @return The valid HashCode1 of Credit Card 
   * OR Card number itself, in case if encryption if off.
   * @rep:displayname Get Credit Card HashCode1
   */

   public String getCCHashCode1()
   {
     return ccHashCode1;
   }
  
  /**
   * Retrieves the HashCode2 of the given credit number. 
   *
   * @return The valid HashCode2 of Credit Card 
   * OR Card number itself, in case if encryption if off.
   * @rep:displayname Get Credit Card HashCode2
   */

   public String getCCHashCode2()
   {
     return ccHashCode2;
   }


  /**
   * Retrieves the tax exemption flag. The method returns 'E' if the order is tax exempt,
   * or 'S' otherwise.
   *
   *
   * @return The tax exemption flag.
   * @rep:displayname Get Tax Exempt Flag
   */
  public String getTaxExemptFlag()
  {
    return taxExemptFlag;
  }

  /**
   * Sets the tax exempt flag. 
   * 
   * 
   * @param taxExemptFlag The tax exempt flag. Possible values are 'E' for tax exemption, and 'S' for non taxexempt orders.
   * @rep:displayname Set Tax Exempt Flag
   */
  public void setTaxExemptFlag(String taxExemptFlag)
  {
    this.taxExemptFlag = taxExemptFlag;
  }

  /**
   * retrieves the tax reason code if the order is tax exempt.
   * 
   *
   * @return The tax reason code.
   * @rep:displayname Get Tax Reason Code
   */
  public String getTaxReasonCode()
  {
    return taxReasonCode;
  }

  /**
   * Sets the tax reason code if the order is tax exempt.
   * 
   * @param taxReasonCode The tax exemption code.
   * @rep:displayname Set Tax Reason Code
   */
  public void setTaxReasonCode(String taxReasonCode)
  {
    this.taxReasonCode = taxReasonCode;
  }

  /**
   * Retrieves the tax certificate number if the order is tax exempt.
   * 
   *
   * @return The tax certificate number.
   * @rep:displayname Get Tax Certificate Number
   */
  public String getTaxCertificateNumber()
  {
    return taxCertificateNumber;
  }

  /**
   * Sets the tax certificate number if the order is tax exempt for a save operation.
   *
   *
   * @param taxCertificateNumber The tax certificate number.
   * @rep:displayname Set Tax Certificate Number
   */
  public void setTaxCertificateNumber(String taxCertificateNumber)
  {
    this.taxCertificateNumber = taxCertificateNumber;
  }

  /**
   * Retrieves the order number when the order is placed.
   *
   *
   * @return The order number.
   * @rep:displayname Get Order Number
   */
  public String getOrderNumber()
  {
    return orderNumber;
  }

  /**
   * Retrieves the purchase order number attached to the cart.
   *
   *
   * @return The purchase order number.
   * @rep:displayname Get Purchase Order Number
   * 
   */
  public String getPoNumber()
  {
    return `;
  }

  /**
   * Returns Contract object if the cart is associated with a Contract
   *
   *
   * @return Contract - contract associated to the cart
   */
  public Contract getAssociatedContract()
  {
    return associatedContract;
  }

  /**
   * Retrieves the Agreement object of the cart.
   *
   * @return The {@link oracle.apps.ibe.shoppingcart.quote.Agreement Agreement} object at cart level.
   * @rep:displayname Get Agreement Info
   */
  public Agreement getAgreementInfo()
  {
    return agreementInfo;
  }

  /**
   * Sets Agreement object for saving the header agreement info
   *
   * @return Agreement - Agreement set for cart header
   */
  public void setAgreementInfo(Agreement agreementInfo)
  {
    this.agreementInfo = agreementInfo;
  }

  /**
   * Returns true if the cart contains a serviceable item
   *
   *
   * @return boolean - flag to indicate presence of serviceable items
   */
  public boolean containsServiceableItems()
  {
    return containsSVAFlag;
  }

  /**
   * Returns true if the cart is 'orderable'. At this time the flag
   * is set to false only if the cart contains configurations that are
   * either invalid or incomplete.
   * <BR>Note: This flag can be used only after calling load related apis which
   * also load the items in the cart, e.g. loadWithItems,
   * loadWithPaymentAndShipment etc.
   * @return boolean - flag to indicate whether an order can be placed with this
   * cart.
   */
  public boolean isOrderable()
  {
    return isOrderable;
  }

  /**
   * Retrieves the currency code at the cart level.
   *
   *
   * @return The currency code at the cart level.
   * @rep:displayname Get Currency Code
   */
  public String getCurrencyCode()
  {
    return currencyCode;
  }


  /**
   * Returns true if the cart has been published by a sales rep
   *
   *
   * @return boolean - flag to indicate cart publishing status
   */
  public boolean isPublished()
  {
    return isPublished;
  }

  /**
   * Returns true if the cart contains one or more unpublished items.
   *
   *
   * @return boolean - flag to indicate whether cart has any unpublished items
   */
  public boolean containsUnorderableItems()
  {
    return containsUnorderableItems;
  }

  /**
   * Returns quote status id as string.
   *
   * @return string - quote status id
   */
  public String getStatusId()
  {
    return this.headerRec.quote_status_id.toString();
  }

  /**
   * Retrieves the name of the Sold To customer.
   *
   * @return The name of the Sold To customer.
   * @rep:displayname Get Sold To Customer Name
   */
  public String getSoldToCustomerName()
  {
    return headerRec.party_name;
  }

  /**
   * Returns contract template id associated with the cart
   *
   * @return string - contract template id
   */
  public String getContractTemplateId()
  {
    return this.contract_template_id;
  }
  /**
   * Returns contract template major version associated with the cart
   *
   * @return string - contract template major version
   */
  public String getContractTemplateMajorVersion()
  {
    return this.contract_template_major_ver;
  }

  /**
  * Retrieves the unique identifier of the site in which the cart was created.
  *
  * @return The {@link oracle.apps.ibe.minisites.Minisite Minisite} identifier.
  * @rep:displayname Get Minisite Identifier
  */
  public String getMinisiteId()
  {
    return this.minisiteId;
  }
    
  /**
   * Sets the context for the Add-To-Cart operation. Use either
   * ADDTO_ACTIVE_CART for a normal "addtocart" operation,
   * ADDTO_EXPRESS_ORDER to perform an express checkout,
   * or UPDATE_EXPRESS_ORDER to update an express order.
   * The default value of the saveContext is ADDTO_ACTIVE_CART.
   *
   * @param addToCartContext The context for the Add-To-Cart operation.
   * @rep:displayname Set Add to Cart Context
   * 
   */
  public void setAddToCartContext(int addToCartContext)
  {
    this.addToCartContext = addToCartContext;
  }

  /**
   * Returns true if the cart has to be reloaded because a save operation
   * failed because the cart had already been updated prior to the save
   * operation. This flag will be set only after a save operation like
   * updateQuantity, updateSupportLevel, saveShippingInformation, save<xxx>Payment
   * etc.
   * @return boolean - flag to indicate whether cart needs to be reloaded.
   */
  public boolean needsReload()
  {
    return reloadFlag;
  }

  /**
   * Returns true if the cart contains one or more promotional goods items.
   *
   *
   * @return boolean - flag to indicate whether cart has any promotional goods items
   */
  public boolean containsPRGItems()
  {
    return containsPRGItems;
  }

  /*
   * public static void reprice()
   * throws FrameworkException, SQLException, ShoppingCartException
   * {
   * }
   * 
   * public void addItems(ShoppingCartItem[] shopCartItems)
   * throws FrameworkException, SQLException, ShoppingCartException
   * {
   * }
   */

  /**
   * Updates the quantity of items in the shopping cart. To call this API, the
   * developer will need to set the shopping cart items in the cart using the
   * setShoppingCartItems method and then call this API. Each ShoppingCartItem will
   * need to contain the cart line id, uom code, inventory item id, item type
   * and the new quantity.
   * This signature calls the other updateQuantity signature with both calculateTax and calculateFreight parameters set to false.
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateQuantity()
          throws FrameworkException, SQLException, QuoteException, 
                 ShoppingCartException
  {
    updateQuantity(false, false);
  }


  /**
   * Updates the quantity of items in the shopping cart.<BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method. however, this should not be set if express ordering the items
   * <LI>shopCartItems array via setShoppingCartItems method (Each ShoppingCartItem will
   * need to contain the cart line id, uom code, inventory item id, item type
   * and the new quantity.)  The quantities will be validated by a call to Item.validateQuantity
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <LI>addToCartContext via setAddToCartContext (if deleting lines from an express
   * order, this needs to be set to UPDATE_EXPRESS_ORDER; otherwise, the default
   * will be to update a cart/quote)<BR>
   * This context needs to be set for the sake of validating that the operation
   * is being done upon the correctly expected type of quote. (ex/ if the method
   * is being invoked for what is thought to be a normal cart, but in the db, it has been
   * turned into an express order, the validation will fail)
   * <BR><BR>
   * Internally, this api sets the headerRec, controlRec (if set to do pricing), lineRec array, and calls Quote.save with all boolean flags set to false with the following exceptions:
   * <LI>saveLine is set to true (and all the lineRec entries will have UPDATE as the operation code)
   * <LI>In addition, sets the sharee parameters according to info in the cookie, and passes a saveType depending on the addToCartContext set.
   * <BR><BR>
   * Please see the comments for Quote.save for more information.

   * <BR><BR>If both input parameters are set to false, pricing will not be calculated.
   * @param - calculateTax - indicates whether tax needs to be calculated
   * @param - calculateFreight - indicates whether shipping and handling charges
   * needs to be calculated
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateQuantity(boolean calculateTax, boolean calculateFreight)
          throws FrameworkException, SQLException, QuoteException, 
                 ShoppingCartException, CatalogException
  {

    String  METHOD = "updateQuantity";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
    IBEUtil.log(CLASS, METHOD, "calculateTax = " + calculateTax);
    IBEUtil.log(CLASS, METHOD, "calculateFreight = " + calculateFreight);
}
    ShoppingCartItem  scartItem = null;
    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());
    MessageManagerInter mmi = Architecture.getMessageManagerInstance();
	boolean bStopWhenInvalid = true;
    // handle only SVAs and STDs for the time being
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "num of items being updated = " + numCartItems);
}
      if(numCartItems > 0)
      {
        try
        {
          BigDecimal[]  bigQuantities = new BigDecimal[numCartItems];
          BigDecimal[]  cartLineIds = new BigDecimal[numCartItems];
          BigDecimal[]  bigOrgIds = new BigDecimal[numCartItems];
          
          String[] carLinesErrorMsgs = new String[numCartItems];

          int           numLineRecords = 0;
          // int           numLineShipRecords = 0;
          int           numLineDtlRecords = 0;
          int           svaorstd = 0;
          ArrayList     tmpArrayList = new ArrayList();

          for(int i = 0; i < numCartItems; i++)
          {
            scartItem = shopCartItems[i];

            if((scartItem.cartLineId.equals(""))
                    || (scartItem.inventoryItemId.equals(""))
                    || (scartItem.itemType.equals(""))
                    || (scartItem.quantity.equals("")))
            {
            	carLinesErrorMsgs[i] = mmi.getMessage("IBE_SC_CARTITEM_INCOMPLETE");
              //throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");
            }

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking cart line id = " + scartItem.cartLineId);
            IBEUtil.log(CLASS, METHOD, "inventoryItemId = " + scartItem.inventoryItemId);
             IBEUtil.log(CLASS, METHOD, "itemType = " + scartItem.itemType);

			}
			
			
            if(!(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
                    &&!(scartItem.itemType.equals(ShoppingCartItem.STANDARD_ITEM_TYPE))
                    &&!(scartItem.itemType.equals(ShoppingCartItem.MODEL_ITEM_TYPE)))
            {
            	carLinesErrorMsgs[i] = mmi.getMessage("IBE_SC_CANT_UPDATE_QTY");
              //throw new ShoppingCartException("IBE_SC_CANT_UPDATE_QTY");
            }

            try
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Qty being updated to = " + scartItem.quantity);
			}
              bigQuantities[svaorstd] = numFormat.parseNumber(scartItem.quantity);
              //if (bigQuantities[svaorstd] == null)
            	//  carLinesErrorMsgs[i] = mmi.getMessage("IBE_CT_INVALID_QUANTITY");
                 //throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            catch(NumberFormatException e)
            {
              //throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            catch(StringIndexOutOfBoundsException e)
            {
              //throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            catch(Exception e)
            {
              //throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }

            /*if(bigQuantities[svaorstd].doubleValue() <= 0)
            {
              throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
            }*/

            cartLineIds[svaorstd] = new BigDecimal(scartItem.cartLineId);

            int svcItemsLength = 0;

            if(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
            {
              if(scartItem.svcItems != null)
              {
                svcItemsLength = scartItem.svcItems.length;
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of service items is " + svcItemsLength);
}
                if(svcItemsLength <= 0)
                {
                  throw new ShoppingCartException("IBE_SC_NUM_SVC_ITMS_ZERO");
                }
              }
            }


            numLineRecords += (svcItemsLength + 1);

            // numLineShipRecords += (svcItemsLength + 1);

            tmpArrayList.add(scartItem);

            svaorstd++;

          }   // end of for loop


          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of items being updated are " + svaorstd);

}
          int[]         itemIds = new int[svaorstd];
          BigDecimal[]  orgIds = new BigDecimal[svaorstd];
          String[]      strQuantities = new String[svaorstd];
          String[]      uomCodes = new String[svaorstd];
		  
		  String[] errormsgs = new String[svaorstd];
		  int sizeerrorMsg = errormsgs.length;
		  IBEUtil.log(CLASS, METHOD, " sizeerrorMsg Value is = " +sizeerrorMsg);
		  String quantities = "0";

          shopCartItems = new ShoppingCartItem[svaorstd];
          boolean isInvalid = false;
          for(int i = 0; i < svaorstd; i++)
          {
            shopCartItems[i] = (ShoppingCartItem) tmpArrayList.get(i);
            // populate arrays for Item.validateQuantity
            strQuantities[i] = shopCartItems[i].quantity;
            uomCodes[i] = shopCartItems[i].uom;
            itemIds[i] = Integer.parseInt(shopCartItems[i].inventoryItemId);
            orgIds[i] = new BigDecimal(shopCartItems[i].organizationId);
			//Code Added for CHR 397
            
				OracleConnection oracleconnection = null;        
				OraclePreparedStatement oraclepreparedstatement = null;
				ResultSet resultset = null;        
				String sSQLQuery1 ="";
				String mOQValue = "1";
				String fLMValue = "1";
				String segment = "";
				
			try{
					oracleconnection = (OracleConnection)TransactionScope.getConnection();
					StringBuffer stringbuffer = new StringBuffer(500);

					stringbuffer.append(" select MINIMUM_ORDER_QUANTITY , FIXED_LOT_MULTIPLIER , SEGMENT1");            
					stringbuffer.append(" FROM mtl_system_items_b ");            
					stringbuffer.append(" WHERE ");            
					stringbuffer.append(" inventory_item_id = ? ");
					stringbuffer.append(" AND ORGANIZATION_ID = ?");
							
					sSQLQuery1 = stringbuffer.toString();
					IBEUtil.log(CLASS, METHOD, "sSQLQuery = " +sSQLQuery1);
					oraclepreparedstatement = (OraclePreparedStatement)oracleconnection.prepareStatement(sSQLQuery1);
					oraclepreparedstatement.setInt(1, itemIds[i]); 
					oraclepreparedstatement.setBigDecimal(2, orgIds[i]); 

					resultset = oraclepreparedstatement.executeQuery(); 
					IBEUtil.log(CLASS, METHOD, "resultset = " +resultset);
					if(resultset != null && resultset.next()){
						mOQValue = resultset.getString(1);  
						fLMValue = resultset.getString(2);
						segment  = resultset.getString(3);
						IBEUtil.log(CLASS, METHOD, " mOQValue Inside If Condition = " +mOQValue);
						IBEUtil.log(CLASS, METHOD, " fLMValue Inside If Condition = " +fLMValue);
						IBEUtil.log(CLASS, METHOD, " segment Inside If Condition = " +segment);
					}					
				}
				catch(Exception e)
				{
				  mOQValue = "1";
				  fLMValue = "1";
				  IBEUtil.log(CLASS, METHOD, "Catch Block " +mOQValue);
				  IBEUtil.log(CLASS, METHOD, "fLMValue Catch Block " +fLMValue);
				}
				finally
				{
					         
					if(oracleconnection != null)                
						TransactionScope.releaseConnection(oracleconnection); 
			    }
			
			BigDecimal remaind;
				BigDecimal flmremaind;
				int flmremaind1 = 0;
				BigDecimal flmremainderValue;
				int flmremValue = 0;
				int submoqbig = 0;
				int flmremainderValue1 = 0;
								
				if(fLMValue == null){
				fLMValue = "1";
				IBEUtil.log(CLASS, METHOD, " fLMValue Equals condition value " +fLMValue);
				}
				if(mOQValue == null){
				mOQValue = "1";
				IBEUtil.log(CLASS, METHOD, " mOQValue Equals condition value " +mOQValue);
				}
				int remaind1 = 0;				
				IBEUtil.log(CLASS, METHOD, "remaind1 value is " +remaind1);
				BigDecimal moqval = new BigDecimal(mOQValue);
				IBEUtil.log(CLASS, METHOD, " moqval value " +moqval);
				BigDecimal flmval = new BigDecimal(fLMValue);
				IBEUtil.log(CLASS, METHOD, "flmval value is " +flmval);	
				int flmValue = flmval.intValue();
				int moqValueint = moqval.intValue();
				IBEUtil.log(CLASS, METHOD, "moqValueint value is " +moqValueint);
				IBEUtil.log(CLASS, METHOD, "flmValue value is " +flmValue);
				if(bigQuantities[i] != null){
					remaind = bigQuantities[i].remainder(moqval);
					IBEUtil.log(CLASS, METHOD, "remaind value is " +remaind);
					
					flmremainderValue = bigQuantities[i].remainder(flmval);
					IBEUtil.log(CLASS, METHOD, "flmremainderValue value is " +flmremainderValue);
					
					flmremainderValue1 = flmremainderValue.intValue();
					IBEUtil.log(CLASS, METHOD, "flmremainderValue1 value is " +flmremainderValue1);
					
					int remaindValue = remaind.intValue();
					
					flmremaind = bigQuantities[i].remainder(flmval);
					IBEUtil.log(CLASS, METHOD, "flmremaind value is " +flmremaind);
					
					int remainder = flmremaind.intValue();
					IBEUtil.log(CLASS, METHOD, "remainder value is " +remainder);
					
					int bigvalue = bigQuantities[i].intValue();
					IBEUtil.log(CLASS, METHOD, "bigvalue value is " +bigvalue);
					
					 submoqbig = moqValueint - bigvalue;
					IBEUtil.log(CLASS, METHOD, "submoqbig value is " +submoqbig);
					
					flmremaind1 = bigQuantities[i].compareTo(moqval);
					IBEUtil.log(CLASS, METHOD, "flmremaind1 value is " +flmremaind1);
					
					flmremValue = bigQuantities[i].compareTo(flmval);
					IBEUtil.log(CLASS, METHOD, "flmremValue value is " +flmremValue);
				}else{
					isInvalid = true;					
				}
				
			if(flmValue == 1 && submoqbig > 0){
			IBEUtil.log(CLASS, METHOD, "Inside If condition value is submoqbig");
			isInvalid = true;
			}else					
			if(flmValue != 1 && flmremaind1 < 0) {
			IBEUtil.log(CLASS, METHOD, "Inside flmValue is and MOQvalue comparison");
			isInvalid = true;
			}else
			if(flmValue != 1 && flmremainderValue1 !=remaind1){
			IBEUtil.log(CLASS, METHOD, "Inside If condition value is remaindValue");
			isInvalid = true;
			}else if((strQuantities[i].compareTo(quantities)) <= 0){
				isInvalid = true;				
			}
			
			if(isInvalid){
			String as21[] = new String[2];
			if (mOQValue == null){
			IBEUtil.log(CLASS, METHOD, "If block MOQValue is = ");	
			//errormsgs[i] = mmi.getMessage("IBE_SC_QTY_ZERO_NEGATIVE");			
			}
			else{
			Object[] params = new String[3];
			try {
            params[0] = Item.load(itemIds[i]).getPartNumber();
          } catch (Exception e)
          {
            params[0] = "";
          }
		 if(mOQValue != "1" && fLMValue !="1"){          
		   params[1] = mOQValue;
			params[2] = fLMValue;
          IBEUtil.log(CLASS, METHOD, "Else block MOQValue is = ");	
			errormsgs[i] = mmi.getMessage("XXIBE_MIN_ORDER_QUANTITY",params);
			IBEUtil.log(CLASS, METHOD, "Inside If inatech1 Value"+errormsgs[i]);
          //throw new QuoteException("", "XXIBE_MIN_ORDER_QUANTITY", params);
			}
			else if(mOQValue == "1" && fLMValue !="1"){
			params[1] = fLMValue;
			errormsgs[i] = mmi.getMessage("XXIBE_MIN_FLM_VALUE",params);
			IBEUtil.log(CLASS, METHOD, "Inside If inatech3 Value"+errormsgs[i]);
			//throw new QuoteException("", "XXIBE_MIN_FLM_VALUE", params);
			}
			else{
			params[1] = mOQValue;
			errormsgs[i] = mmi.getMessage("XXIBE_MIN_MOQ_VALUE",params);
			IBEUtil.log(CLASS, METHOD, "Inside If inatech2 Value"+errormsgs[i]);
			//throw new QuoteException("", "XXIBE_MIN_MOQ_VALUE", params);
			}			
		 
		 if((strQuantities[i].compareTo(quantities)) <= 0 && mOQValue == "1" && fLMValue == "1")
         {
			 errormsgs[i] = mmi.getMessage("XXIBE_SC_QTY_ZERO_NEGATIVE");
           //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
         }
		
			 if((strQuantities[i].compareTo(quantities)) <= 0 && mOQValue != "1"  && fLMValue == "1")
	            {
				 params[1] = mOQValue;				 
				 errormsgs[i] = mmi.getMessage("XXIBE_MIN_MOQ_VALUE",params);
	              //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
	            }
				
				if((strQuantities[i].compareTo(quantities)) <= 0 && mOQValue != "1"  && fLMValue != "1")
	            {
				 params[1] = mOQValue;
				 params[2] = fLMValue;
				 errormsgs[i] = mmi.getMessage("XXIBE_MIN_ORDER_QUANTITY",params);
	              //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
	            }
				
				
			 if(bigQuantities[i] == null && mOQValue == "1" && fLMValue == "1")
	            {
				 errormsgs[i] = mmi.getMessage("XXIBE_CT_INVALID_QUANTITY");
	              //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
	            }
			 if(bigQuantities[i] == null && mOQValue != "1" && fLMValue == "1")
	            {
				 params[1] = mOQValue;
				 errormsgs[i] = mmi.getMessage("XXIBE_MIN_MOQ_VALUE",params);
	              //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
	            }
				
				if(bigQuantities[i] == null && mOQValue != "1" && fLMValue != "1")
	            {
				 params[1] = mOQValue;
				 params[2] = fLMValue;
				 errormsgs[i] = mmi.getMessage("XXIBE_MIN_ORDER_QUANTITY",params);
	              //throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
	            }
			
			}
		}	
	
					
          }
		  if(isInvalid){
		  IBEUtil.log(CLASS, METHOD, "Inside If inatech456 Value");
		  int nooferrorMsgs = errormsgs.length;
		  IBEUtil.log(CLASS, METHOD, "No of Error Messages"+nooferrorMsgs);
		  IBEUtil.log(CLASS, METHOD, "Error Messages are"+errormsgs);
		  for (int j=0; j<nooferrorMsgs; j++)
			{
				IBEUtil.log(CLASS, METHOD, "Error Messages are11"+errormsgs[j]);
			}	  
		  
		  throw new QuoteException("\n",errormsgs);
		  }		
		  
			// End of code for CHR397	  
		            
          try
          {
		  if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Item.validateQuantity");
					}
            Item.validateQuantity(itemIds, orgIds, strQuantities, uomCodes, true);
          } catch (CatalogException e)
          {
            throw new ShoppingCartException("", e);
          }
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Item.validateQuantity");

}
          BigDecimal  quoteHeaderId = new BigDecimal(cartId);

          // now all the items are validated. Now set up line records,
          // line shipment records, service items in a loop

          /*
           * if(lineShipmentRec == null)
           * {
           * lineShipmentRec = new ShipmentRecord[numLineShipRecords];
           * }
           */

          if(lineRec == null)
          {
            lineRec = new LineRecord[numLineRecords];
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numLineRecords is " + numLineRecords);
}
          }

          /*
           * if(lineDetRec == null)
           * {
           * lineDetRec = new LineDetailRecord[numLineDtlRecords];
           * }
           */

          int[] lineRecIndex = new int[1];
          int[] lineDtlRecIndex = new int[1];

          lineRecIndex[0] = 0;
          lineDtlRecIndex[0] = 0;

          for(int i = 0; i < svaorstd; i++)
          {
            scartItem = shopCartItems[i];

            if(lineRec[lineRecIndex[0]] == null)
            {
              lineRec[lineRecIndex[0]] =
                new oracle.apps.ibe.shoppingcart.quote.LineRecord();
            }

            /*
             * if(lineShipmentRec[lineRecIndex[0]] == null)
             * {
             * lineShipmentRec[lineRecIndex[0]] =
             * new oracle.apps.ibe.shoppingcart.quote.ShipmentRecord();
             * }
             */

            ShoppingCartUtil.setupLineRecord(lineRec[lineRecIndex[0]],
                                             UPDATE_OPCODE, quoteHeaderId,
                                             cartLineIds[i],
                                             bigQuantities[i],
                                             new BigDecimal(scartItem.inventoryItemId),
                                             orgIds[i], scartItem.uom,
                                             scartItem.itemType, null, null,
                                             null);

            /*
             * setupLineShipmentRecord(lineShipmentRec[lineRecIndex[0]],
             * UPDATE_OPCODE, quoteHeaderId,
             * cartLineIds[i],
             * new BigDecimal(scartItem.shipmentId),
             * bigQuantities[i]);
             */

            lineRecIndex[0]++;

            if((scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
                    && (scartItem.svcItems != null))
            {
              setupServices(scartItem, bigQuantities[i], UPDATE_OPCODE,
                            lineRecIndex, lineDtlRecIndex);
            }
          }   // end of for loop

          String  calcTax = (calculateTax) ? YES : NO;
          String  calcFreight = (calculateFreight) ? YES : NO;
          boolean priceRecalcFlag = (calculateTax || calculateFreight);

          setupControlRecord(calcTax, calcFreight, priceRecalcFlag);
          setupHeaderRecord(quoteHeaderId);

          // save the quote
          BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
          BigDecimal  partyId = null;
          BigDecimal  acctId = null;

          if(shareeNumber != null)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
            partyId = RequestCtx.getPartyId();
            acctId = RequestCtx.getAccountId();
          }

          try
          {
            int saveType = this.SAVE_NORMAL;
            if (addToCartContext == this.UPDATE_EXPRESS_ORDER) saveType = this.UPDATE_EXPRESSORDER;

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.save");
}
            save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
                 true, false, false, false, false, false, false, false,
                 false, false, false, false, false, false, false, false,
                 false,saveType);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling Quote.save");

}
          }
          catch(QuoteException e)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
            checkUpdateTimestamp(e);
          }

          /*
           * } else
           * {
           * save(RequestCtx.getPartyId(), RequestCtx.getAccountId(),
           * RequestCtx.getShareeNumber(), Quote.SEPARATE_LINES, false,
           * true, true, false, false, false, false, false, false, false,
           * false, true, false, false, false, false, false, false);
           * }
           */

        }
        catch(NumberFormatException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
        catch(StringIndexOutOfBoundsException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
      }
      else
      {
        throw new ShoppingCartException("IBE_SC_NOTHING_TO_UPDATE");
      }
    }
    else
    {
      throw new ShoppingCartException("IBE_SC_NOTHING_TO_UPDATE");
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}


  }

 /**
  * Deletes items from the cart or pending express order depending on the addToCartContext setting.
  * 
  * The method expects the following to be set:
  * <LI>cartId via a constructor or the setCartId method.
  * <LI>shopCartItems array via setShoppingCartItems method (the cartLineId will need to be set for each line to be deleted).
  * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp.
  * <LI>addToCartContext via setAddToCartContext 
  * (if deleting lines from an expressorder, this needs to be set to UPDATE_EXPRESS_ORDER; 
  *  otherwise, the default is to update a cart/quote)
  * 
  *
  * @return The unique identifier of quote updated as a result of this operation.
  * @throws FrameworkException if there is a framework layer error
  * @throws SQLException if there is a database error
  * @throws ShoppingCartException the error message will indicate the nature of the error
  * @rep:displayname Remove Items
  */
  public void removeItems()
          throws FrameworkException, SQLException, ShoppingCartException,
                 QuoteException
  {
    String  METHOD = "removeItems";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems = " + numCartItems);

}
      if(numCartItems > 0)
      {
        try
        {

          BigDecimal  quoteHeaderId = new BigDecimal(cartId);

          if(lineRec == null)
          {
            lineRec =
              new oracle.apps.ibe.shoppingcart.quote.LineRecord[numCartItems];

          }

          for(int i = 0; i < numCartItems; i++)
          {
            if(lineRec[i] == null)
            {
              lineRec[i] = 
                new oracle.apps.ibe.shoppingcart.quote.LineRecord();
            }
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cart Line Id = " + shopCartItems[i].cartLineId);
}
            ShoppingCartUtil.setupLineRecord(lineRec[i], DELETE_OPCODE, 
                                             quoteHeaderId,
                                             new BigDecimal(shopCartItems[i].cartLineId),
                                             null, null, null, null, null, 
                                             null, null, null);

          }

          setupControlRecord(YES, YES, true);
          setupHeaderRecord(quoteHeaderId);

          BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
          BigDecimal  partyId = null;
          BigDecimal  acctId = null;

          if(shareeNumber != null)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
            partyId = RequestCtx.getPartyId();
            acctId = RequestCtx.getAccountId();
          }

          try
          {
            int saveType = this.SAVE_NORMAL;
            if (addToCartContext == this.UPDATE_EXPRESS_ORDER) saveType = this.UPDATE_EXPRESSORDER;

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling quote.save");
}
            save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
                 true, false, false, false, false, false, false, false,
                 false, false, true, false, false, false, false, false,
                 false, saveType);
          }
          catch(QuoteException e)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
            checkUpdateTimestamp(e);
          }
        }
        catch(NumberFormatException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
        catch(StringIndexOutOfBoundsException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
      }
      else
      {
        throw new ShoppingCartException("IBE_SC_NOTHING_TO_DEL");
      }
    }
    else
    {
      throw new ShoppingCartException("IBE_SC_NOTHING_TO_DEL");
    }
  }

  /**
   * Removes all items in the cart.
   *
   *
   * @param cartId - the cart id
   * @param lastModifiedTimestamp - the last modified time stamp of the cart.
   * @param shareeNumber - a sharee number if one exists.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  // not used in iStore code as of IBE.P (the button is no longer avl)   
  public static void removeAllItems(String cartId,
                                    String lastModifiedTimestamp, 
                                    String shareeNumber) throws FrameworkException,
                                    SQLException, ShoppingCartException, 
                                    QuoteException
  {
    String METHOD = "removeAllItems";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId is " + cartId);
    IBEUtil.log(CLASS, METHOD, "shareeNumber is " + shareeNumber);

}
    BigDecimal  shareeNumberBig = null;

    try
    {
      if((shareeNumber != null) && (!shareeNumber.equals(EMPTY_STRING)))
      {
        shareeNumberBig = new BigDecimal(shareeNumber);

      }

      java.sql.Timestamp  cartDate = Timestamp.valueOf(lastModifiedTimestamp);

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLing Quote.deleteAllLines");
}
      Quote.deleteAllLines(new BigDecimal(cartId), cartDate, shareeNumberBig);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done CALLing Quote.deleteAllLines");
}
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Updates the support level of the cart. Prior to calling this method, the user
   * will need to set the serviceable items into the shopping cart by calling the
   * the setShoppingCartItems method. Each serviceable item will need to have the
   * the cart line id and its service items (set by calling the setServiceItems method)
   * set within it. Each of the service item will need to contain the cart line id
   * and the inventory item id (the id of the current service item) set within it.
   * <P> This API does not recalculate tax and freight charges. Price is also
   * not recalculated.
   * @param newServiceIds - the inventory item ids of the items that map to the
   * new service level.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateSupportLevel(String[] newServiceIds)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    updateSupportLevel(newServiceIds, false, false);
  }

  /**
   * Updates the support level of the cart. Prior to calling this method, the user
   * will need to set the serviceable items into the shopping cart by calling the
   * the setShoppingCartItems method. Each serviceable item will need to have the
   * the cart line id and its service items (set by calling the setServiceItems method)
   * set within it. Each of the service item will need to contain the cart line id
   * and the inventory item id (the id of the current service item) set within it.
   * @param newServiceIds - the inventory item ids of the items that map to the
   * new service level.
   * @param calculateTax - flag to indicate if tax needs to be recalculated
   * @param calculateFreight - flag to indicate if freight needs to be recalculated
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateSupportLevel(String[] newServiceIds,
                                 boolean calculateTax,
                                 boolean calculateFreight) throws FrameworkException,
                                 SQLException, QuoteException,
                                 ShoppingCartException
  {

    String  METHOD = "updateSupportLevel";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
    IBEUtil.log(CLASS, METHOD, "calculateTax = " + calculateTax);
    IBEUtil.log(CLASS, METHOD, "calculateFreight = " + calculateFreight);
}
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems = " + numCartItems);

}
      if(numCartItems > 0)
      {
        //################################################################//
        // MAJOR PART OF THE CODE FOR THIS API IS DONE BY THIS METHOD
        //################################################################//
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling updateSupportAndOrQuantity");
}
        updateSupportAndOrQuantity(UPDATE_SERVICE_ONLY,
                                  newServiceIds,
                                  calculateTax,
                                  calculateFreight);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Back from updateSupportAndOrQuantity");
}
      }
      else
      {
        throw new ShoppingCartException("IBE_SC_CART_EMPTY");
      }
    }
    else
    {
      throw new ShoppingCartException("IBE_SC_CART_EMPTY");
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
       }
  }

  /**
   * Updates the quantities of items in the cart and also updates the
   * support level of the cart. To call this API, the
   * developer will need to set the shopping cart items in the cart using the
   * setShoppingCartItems method and then call this API. Each ShoppingCartItem will
   * need to contain the cart line id, uom code, inventory item id, item type
   * and the new quantity.
   *
   * @param newServiceIds - the inventory item ids of the items that map to the
   * new service level.
   * @param calculateTax - flag to indicate if tax needs to be recalculated
   * @param calculateFreight - flag to indicate if freight needs to be recalculated
   * <P>If either of the tax or freight flags are set to true, price is also
   * recalculated.
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateQuantityAndSupportLevel(String[] newServiceIds,
                                            boolean calculateTax,
                                            boolean calculateFreight) throws FrameworkException,
                                            SQLException, QuoteException,
                                            ShoppingCartException
  {
    String  METHOD = "updateQuantityAndSupportLevel";
    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());
    
if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
   IBEUtil.log(CLASS, METHOD, "calculateTax = " + calculateTax);
    IBEUtil.log(CLASS, METHOD, "calculateFreight = " + calculateFreight);
}
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numcartitems is " + numCartItems);
}
      if(numCartItems > 0)
      {
        ShoppingCartItem  scartItem = null;

        BigDecimal[]      bigQuantities = new BigDecimal[numCartItems];

        int               svaorstd = 0;
        ArrayList         tmpArrayList = new ArrayList();

        try
        {

          for(int i = 0; i < numCartItems; i++)
          {
            scartItem = shopCartItems[i];

            if((scartItem.cartLineId.equals(""))
                    || (scartItem.inventoryItemId.equals(""))
                    || (scartItem.itemType.equals(""))
                    || (scartItem.quantity.equals("")))
            {
              throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");
            }

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking cart line id = " + scartItem.cartLineId);
            IBEUtil.log(CLASS, METHOD, "inventoryItemId = " + scartItem.inventoryItemId);
            IBEUtil.log(CLASS, METHOD, "itemType = " + scartItem.itemType);
             }
            if(!(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
                    &&!(scartItem.itemType.equals(ShoppingCartItem.STANDARD_ITEM_TYPE))
                    &&!(scartItem.itemType.equals(ShoppingCartItem.MODEL_ITEM_TYPE)))
            {
              throw new ShoppingCartException("IBE_SC_CANT_UPDATE_QTY");
            }

            try
            {
              bigQuantities[svaorstd] = numFormat.parseNumber(scartItem.quantity);
              if (bigQuantities[svaorstd] == null)
                  throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");

              if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Quantity = " + scartItem.quantity);
}
            }
            catch(NumberFormatException e)
            {
              throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            catch(StringIndexOutOfBoundsException e)
            {
              throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }

            if(bigQuantities[svaorstd].doubleValue() <= 0)
            {
              throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
            }

            int svcItemsLength = 0;

            if(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
            {
              if(scartItem.svcItems != null)
              {
                svcItemsLength = scartItem.svcItems.length;
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num svc items = " + svcItemsLength);
                 }
                if(svcItemsLength <= 0)
                {
                  throw new ShoppingCartException("IBE_SC_NUM_SVC_ITMS_ZERO");
                }
              }
            }

            tmpArrayList.add(scartItem);

            svaorstd++;

          }                                               // end of for loop

          String[]          uomCodes = new String[svaorstd];
          int[]             itemIds = new int[svaorstd];
          BigDecimal[]      orgIds = new BigDecimal[svaorstd];
          String[]          strQuantities = new String[svaorstd];

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Actual items being updated is " + svaorstd);

}
          shopCartItems = new ShoppingCartItem[svaorstd];
          for(int i = 0; i < svaorstd; i++)
          {
            shopCartItems[i] = (ShoppingCartItem) tmpArrayList.get(i);
            // populate arrays for Item.validateQuantity
            strQuantities[i] = shopCartItems[i].quantity;
            uomCodes[i] = shopCartItems[i].uom;
            itemIds[i] = Integer.parseInt(shopCartItems[i].inventoryItemId);
            orgIds[i] = new BigDecimal(shopCartItems[i].organizationId);
          }

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "validating qtys");
}
          try
          {
            Item.validateQuantity(itemIds, orgIds, strQuantities, uomCodes, true);
          } catch (CatalogException e)
          {
            throw new ShoppingCartException("", e);
          }
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "validation done");

}
          //################################################################//
          // MAJOR PART OF THE CODE FOR THIS API IS DONE BY THIS METHOD
          //################################################################//
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling updateSupportAndOrQuantity");
}
          updateSupportAndOrQuantity(UPDATE_QUANTITY_AND_SERVICE,
                                    newServiceIds,
                                    calculateTax,
                                    calculateFreight);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Back from updateSupportAndOrQuantity");
}
        }
        catch(NumberFormatException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
        catch(StringIndexOutOfBoundsException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
        catch(NullPointerException e)
        {
          throw new ShoppingCartException("IBE_SC_INVALID_OPERATION", e);
        }
      }
      else //  if(numCartItems > 0)
      {
        throw new ShoppingCartException("IBE_SC_CART_EMPTY");
      }
    }
    else // if(shopCartItems != null)
    {
      throw new ShoppingCartException("IBE_SC_CART_EMPTY");
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Private support api for updateSupportLevel & updateQuantityAndSupportLevel apis.
   * <BR><BR>
   * Updates the quantities of items in the cart and also updates the
   * support level of the cart. Inherits the requirements of the calling APIs:
   * the developer will need to set the shopping cart items (using the
   * setShoppingCartItems method) and its service items
   * (set by calling the setServiceItems method) set within it.<BR>
   * <BR>
   * Each ShoppingCartItem will need to contain the cart line id, uom code,
   * inventory item id, item type and the new quantity.<BR>
   * <BR>
   * Each of the service items will need to contain the cart line id
   * and the inventory item id (the id of the current service item) set within it.
   * The parent APIs will then then call this API.
   *
   * @param updateContext - private static var either: UPDATE_QUANTITY_AND_SERVICE or UPDATE_SERVICE_ONLY
   * @param newServiceIds - the inventory item ids of the items that map to the
   * new service level.
   * @param calculateTax - flag to indicate if tax needs to be recalculated
   * @param calculateFreight - flag to indicate if freight needs to be recalculated
   * <P>If either of the tax or freight flags are set to true, price is also
   * recalculated.
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  private void updateSupportAndOrQuantity(String updateContext,
                                 String[] newServiceIds,
                                 boolean calculateTax,
                                 boolean calculateFreight
                                 )
  throws FrameworkException,SQLException, QuoteException, ShoppingCartException
  {
    String  METHOD = "updateSupportAndOrQuantity";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
     IBEUtil.log(CLASS, METHOD, "calculateTax = " + calculateTax);
    IBEUtil.log(CLASS, METHOD, "calculateFreight = " + calculateFreight);
     IBEUtil.log(CLASS, METHOD, "updateContext = " + updateContext);
    }
    int numCartItems = shopCartItems.length;
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems = " + numCartItems);

}
    int       numLineDetailRecords = 0;
    int       numLineRecords = 0;

    ArrayList createArray = new ArrayList();
    ArrayList deleteArray = new ArrayList();
    ArrayList updateArray = new ArrayList();

    int       svcItemsLength = 0;
    Object[]  sortedCurrServiceIds = null;
    HashMap   currServiceIdsMap = new HashMap();

    int       newServiceIdsLength = 0;
    String[]  sortedNewServiceIds = null;
    int       sortedNewServiceIdsLength = 0;

    int currCurrServiceId = 0;
    int currNewServiceId = 0;

    if(newServiceIds != null)
    {
      newServiceIdsLength = newServiceIds.length;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Number of New services = " + newServiceIdsLength);
}
      if(newServiceIdsLength > 0)
      {
        sortedNewServiceIds =
          ShoppingCartUtil.sortSvcsArray(newServiceIds);
        sortedNewServiceIdsLength = sortedNewServiceIds.length;
      }
      else
      {
        throw new ShoppingCartException("IBE_SC_NO_SUPP_SPECFD");
      }
    }
    else
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, " Service Level is being set to NONE");
}
    }
    try
    {
      for(int i = 0; i < numCartItems; i++)               // for each item
      {
        // for ibeCScpViewA.jsp which skips non service items by not instantiating the shopCartItem obj
        if (shopCartItems[i] == null) {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Skipping null shopCartItem object...");
}
          continue;
        }

        if (UPDATE_QUANTITY_AND_SERVICE.equals(updateContext)) numLineRecords++;

        ArrayList createRow = new ArrayList();
        ArrayList deleteRow = new ArrayList();
        ArrayList updateRow = new ArrayList();

        if(shopCartItems[i].itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Serviceable itemid: " + shopCartItems[i].getInventoryItemId());

}
          ServiceItem[] svcItems = shopCartItems[i].getServiceItems();
          if (svcItems != null) {
            svcItemsLength = svcItems.length;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Got array of service items length: " + svcItemsLength);

}
            sortedCurrServiceIds =
              ShoppingCartUtil.sortItemsArray(svcItems, currServiceIdsMap);
          }
          int newIndex = 0;
          int currIndex = 0;

          /********** CASE 1: NO OLD SERVICES, CREATE ALL NEW SERVICES *****************************/
          if(svcItems == null && newServiceIds != null)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Got null array of service items, setting to create new service items.");
}
            for(newIndex = 0;
                ((newServiceIdsLength > 0) && (newIndex < sortedNewServiceIdsLength));
                newIndex++)
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service item id to be created: sortedNewServiceIds[" + newIndex + "] = " + sortedNewServiceIds[newIndex]);
}
              // mark this svc for create
              createRow.add(sortedNewServiceIds[newIndex]);
              createArray.add(createRow);
              numLineDetailRecords++;
              numLineRecords++;
            }
          }

          /********** CASE 2: NO NEW SERVICES, DELETE ALL EXISTING *****************************/
          if (svcItems != null && newServiceIds == null) {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Got null array of new service items, setting to delete existing service items.");
}
            for(currIndex = 0; currIndex < svcItemsLength;currIndex++)   // for each SVC
            {
              // mark this svc for delete
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service item id to be deleted = " + svcItems[currIndex].inventoryItemId);
}
              deleteRow.add(String.valueOf(currIndex)); //2827882 - Choosing None should delete all Services 
              deleteArray.add(deleteRow);
              numLineRecords++;
            }
          }
          /********** CASE 3: BOTH NEW SERVICES & OLD SERVICES - FIND OUT WHICH TO CREATE, DELETE, UPDATE *****************/
          if (svcItems != null && newServiceIds != null) {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Have both new services and old services...");
}
            // two array loop; two pointers moving forward only upon meeting conditions
            // newIndex is index into array of NEW service items (should be updated or created)
            // currIndex is index into array of CURRENTLY IN CART service items (should be updated or deleted)
            // if one pointer moves in front of the other, the one that got left behind either needs to be added or deleted
            // if they match, we're updating & both move forward
            currIndex = 0;
            newIndex = 0;

            int compareValue = 0;
            while((currIndex < svcItemsLength) || (newIndex < sortedNewServiceIdsLength))   // for each SVC
            {
              if (currIndex >= svcItemsLength)
                compareValue = 1;
              else  if (newIndex >= sortedNewServiceIdsLength)
                compareValue = -1;
              else {
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "sortedCurrServiceIds[" + currIndex + "] = " + sortedCurrServiceIds[currIndex]);
                IBEUtil.log(CLASS, METHOD, "sortedNewServiceIds[" + newIndex + "] = " + sortedNewServiceIds[newIndex]);
}
                currCurrServiceId = Integer.parseInt((String) sortedCurrServiceIds[currIndex]);
                currNewServiceId = Integer.parseInt(sortedNewServiceIds[newIndex]);
                if (currCurrServiceId < currNewServiceId) compareValue = -1;
                else if (currCurrServiceId == currNewServiceId) compareValue = 0;
                else compareValue = 1;
              }
              // if id's match, we're updating
              if(compareValue == 0)
              {
                // mark this svc for update
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service item id to be updated = " +  sortedCurrServiceIds[currIndex]);
}
                updateRow.add((String) currServiceIdsMap.get((String) sortedCurrServiceIds[currIndex]));
                updateArray.add(updateRow);
                numLineRecords++;
                // move both pointers forward
                currIndex++;
                newIndex++;
                continue;
              }
              // if currId is greater than newId, this means we need to add the newId
              if(compareValue > 0) //
              {
                // mark this svc for create
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service item id to be created = " + sortedNewServiceIds[newIndex]);
}
                createRow.add(sortedNewServiceIds[newIndex]);
                createArray.add(createRow);
                numLineDetailRecords++;
                numLineRecords++;
                // move newIndex forward only
                newIndex++;
                continue;
              }
              // if currId is less than newId, this means we need to delete the currId
              if(compareValue < 0)
              {
                // mark this svc for delete
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service item id to be deleted = " + sortedCurrServiceIds[currIndex]);
}
                deleteRow.add((String) currServiceIdsMap.get((String) sortedCurrServiceIds[currIndex]));

                deleteArray.add(deleteRow);
                numLineRecords++;
                // move currIndex forward only
                currIndex++;
                continue;
              }
            } // end of while loop
          } // end case 3
        } // if(shopCartItems[i].itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
      } // end of i loop

      // now the createArray, deleteArray, and updateArray contain the indices of the svc
      // items to be created, deleted, and updated. Now loop thru the shop cart items
      // and start creating the line record and line detail record

      BigDecimal  quoteHeaderId = new BigDecimal(cartId);
      ShoppingCartItem scartItem = new ShoppingCartItem();
      BigDecimal currQuantity = null;

      int[] lineRecIndex = new int[1];
      int[] lineDtlRecIndex = new int[1];

      lineRecIndex[0] = 0;
      lineDtlRecIndex[0] = 0;
      
      if(lineRec == null)
      {
        lineRec = new LineRecord[numLineRecords];
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numLineRecords = " + numLineRecords);
}
      }

      if((numLineDetailRecords > 0) && (lineDetRec == null))
      {
        lineDetRec =
          new oracle.apps.ibe.shoppingcart.quote.LineDetailRecord[numLineDetailRecords];
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numLineDetailRecords = " + numLineDetailRecords);
}
      }

      NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());
      int serviceCounter = 0;
      for(int i = 0; i < numCartItems; i++)
      {
        // for ibeCScpViewA.jsp which skips non service items by not instantiating the shopCartItem obj
        if (shopCartItems[i] == null) {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Skipping null shopCartItem object...");
}
          continue;
        }

        scartItem = shopCartItems[i];
        currQuantity = numFormat.parseNumber(scartItem.quantity);
        if (currQuantity == null)
            throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
        

        if (UPDATE_QUANTITY_AND_SERVICE.equals(updateContext)) {
          if(lineRec[lineRecIndex[0]] == null)
          {
            lineRec[lineRecIndex[0]] =
                new oracle.apps.ibe.shoppingcart.quote.LineRecord();
          }

          ShoppingCartUtil.setupLineRecord(lineRec[lineRecIndex[0]],
            UPDATE_OPCODE, quoteHeaderId, new BigDecimal(scartItem.cartLineId), currQuantity,
            new BigDecimal(scartItem.inventoryItemId), new BigDecimal(scartItem.organizationId),
            scartItem.uom, scartItem.itemType, null, null, null);
          lineRecIndex[0]++;
        }

        if(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
        {
          // big assumption here is that whatever we do for one serviceable item,
          // we will do for every serviceable item; i.e. if the size of any of these
          // arrays is > 0, they will all be of the same length
          ArrayList tmpCreateRow = null;

          if(createArray.size() > 0)
          {
            tmpCreateRow = (ArrayList) createArray.get(serviceCounter);
          }

          ArrayList tmpDeleteRow = null;

          if(deleteArray.size() > 0)
          {
            tmpDeleteRow = (ArrayList) deleteArray.get(serviceCounter);
          }

          ArrayList tmpUpdateRow = null;
          if(updateArray.size() > 0)
          {
            tmpUpdateRow = (ArrayList) updateArray.get(serviceCounter);
          }
          setupServices(scartItem, currQuantity,
                        lineRecIndex, lineDtlRecIndex, tmpCreateRow,
                        tmpDeleteRow,tmpUpdateRow);
          serviceCounter++;
        } // end if(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
      }    // end of i loop

      String  calcTax = (calculateTax) ? YES : NO;
      String  calcFreight = (calculateFreight) ? YES : NO;
      boolean priceRecalcFlag = (calculateTax || calculateFreight);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "priceRecalcFlag = " + priceRecalcFlag);

}
      setupControlRecord(calcTax, calcFreight, priceRecalcFlag);

      setupHeaderRecord(new BigDecimal(cartId));

      BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
      BigDecimal  partyId = null;
      BigDecimal  acctId = null;

      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }
      int saveType = this.SAVE_NORMAL;
      if (addToCartContext == this.UPDATE_EXPRESS_ORDER) saveType = this.UPDATE_EXPRESSORDER;

      try
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling quote.save");
}
        if(numLineDetailRecords == 0)
        {

          save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES,
               false, true, false, false, false, false, false, false,
               false, false, false, false, false, false, false, false,
               false, false, saveType);
        }
        else
        {
          save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES,
               false, true, true, false, false, false, false, false,
               false, false, false, false, false, false, false, false,
               false, false, saveType);
        }
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling quote.save");
}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(NullPointerException e)
    {
      throw new ShoppingCartException("IBE_SC_INVALID_OPERATION", e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }
  /**
   * Splits an item in the cart into two or more cart lines. The specified cart item object needs
   * to have the unique identifier of item , unique identifier of the item's shipment record, item type, current quantity,
   * UOM code, unique identifier of inventory item set within it.
   * The {@link oracle.apps.ibe.shoppingcart.quote.ShoppingCart Shopping Cart} instance will need to
   * have the unique identifier of the cart set as well.
   *
   *
   * @param scartItem The {@link oracle.apps.ibe.shoppingcart.quote.ShoppingCartItem ShoppingCartItem} object to split.
   * @param tmpSplitQuantities The array of the new (split) quantities.
   *
   * @throws FrameworkException If there is a framework layer error
   * @throws SQLException If there is a database error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of
   * of the error
   * @rep:displayname Split Item
   */
  public void splitItem(ShoppingCartItem scartItem,
                        String[] tmpSplitQuantities) throws FrameworkException,
                        SQLException, QuoteException, ShoppingCartException
  {
    String  METHOD = "splitItem";
    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());    

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "Item qty being split is " + scartItem.quantity);
}
    boolean svcable = false;
    int     svcItems = 1;

    /*
     * validate that scartItem has the following: cartLineId, shipmentId,
     * and quantity (original qty)
     * if((scartItem.cartLineId.equals("")) || (scartItem.shipmentId.equals(""))
     * || (scartItem.itemType.equals(""))
     * || (scartItem.quantity.equals("")) || (scartItem.uom.equals(""))
     * || (scartItem.inventoryItemId.equals("")))
     */

    if((scartItem.cartLineId.equals("")) || (scartItem.itemType.equals(""))
            || (scartItem.quantity.equals("")) || (scartItem.uom.equals(""))
            || (scartItem.inventoryItemId.equals("")))

    {
      throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");

    }

    if(scartItem.itemType.equals(ShoppingCartItem.SERVICEABLE_ITEM_TYPE))
    {
      svcable = true;

      if(scartItem.svcItems != null)
      {
        int svcItemsCount = scartItem.svcItems.length;

        if(svcItemsCount > 0)
        {
          svcItems = svcItemsCount + 1;
        }
      }
    }

    int       tmpSplitQuantitiesLength = tmpSplitQuantities.length;

    ArrayList tmpArray = new ArrayList();

    for(int j = 0; j < tmpSplitQuantitiesLength; j++)
    {
      if(tmpSplitQuantities[j] != null
              && (!tmpSplitQuantities[j].trim().equals("")))
      {
        tmpArray.add(tmpSplitQuantities[j]);
      }
    }

    int       tmpVecSize = tmpArray.size();
    String[]  splitQuantities = new String[tmpVecSize];

    for(int j = 0; j < tmpVecSize; j++)
    {
      splitQuantities[j] = (String) tmpArray.get(j);
       if(logEnabled) { IBEUtil.log(CLASS, METHOD, "split qty is " + splitQuantities[j]);
}
    }

    BigDecimal[]  bigQuantities = null;
    BigDecimal    remainderQty = null;


    try
    {
      if(splitQuantities != null)
      {

        int qtyArrayLength = splitQuantities.length;

        if(qtyArrayLength > 0)
        {

          /*
           * add up the quantities and see if the total exceeds the original quantities
           * as you are doing this, validate the quantities using Item.validate.
           * Check if there is a remainder.
           */
          BigDecimal  origQuantity = numFormat.parseNumber(scartItem.quantity);
          if (origQuantity == null)
              throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
          
          BigDecimal  newQuantity = new BigDecimal((double) 0);

          bigQuantities = new BigDecimal[qtyArrayLength];

          BigDecimal[]  bigOrgIds = new BigDecimal[qtyArrayLength];
          String[]      uomCodes = new String[qtyArrayLength];
          int[]         itemIds = new int[qtyArrayLength];
          BigDecimal    orgId = new BigDecimal(scartItem.organizationId);
          int           itemIdInt =
            Integer.parseInt(scartItem.inventoryItemId);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Id of item being split is " + itemIdInt);
}
          for(int i = 0; i < qtyArrayLength; i++)
          {

            // check for alphanum qtys, negative qtys
            try
            {
              bigQuantities[i] = numFormat.parseNumber(splitQuantities[i]);
              if (bigQuantities[i] == null)
                  throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
              
            }
            catch(NumberFormatException e)
            {
              throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            catch(StringIndexOutOfBoundsException e)
            {
              throw new ShoppingCartException("IBE_CT_INVALID_QUANTITY");
            }
            if(bigQuantities[i].doubleValue() <= 0)
            {
              throw new ShoppingCartException("IBE_SC_QTY_ZERO_NEGATIVE");
            }

            bigOrgIds[i] = orgId;
            uomCodes[i] = scartItem.uom;
            itemIds[i] = itemIdInt;
            newQuantity = newQuantity.add(bigQuantities[i]);
          }

          int recordsLength = qtyArrayLength;

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Total split qty being requested is " + origQuantity);
}
          int compareInt = newQuantity.compareTo(origQuantity);

          if(compareInt > 0)
          {

            // new qty is greater than orig qty
            throw new ShoppingCartException("IBE_SC_QTY_XCEEDED");
          }
          else if(compareInt == 0)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Item.validateQuantity");

}
            try
            {
              Item.validateQuantity(itemIds, bigOrgIds, splitQuantities, uomCodes, true);
            } catch (CatalogException e)
            {
              throw new ShoppingCartException("", e);
            }
            
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Item.validateQuantity");
}
          }
          else if(compareInt < 0)
          {

            // create missing line
            recordsLength += 1;
            remainderQty = origQuantity.add(newQuantity.negate());
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Remainder split qty is " + remainderQty);

}
            BigDecimal[]  nbigOrgIds = new BigDecimal[recordsLength];
            String[]      nuomCodes = new String[recordsLength];
            int[]         nitemIds = new int[recordsLength];
            String[]      nsplitQuantities = new String[recordsLength];

            System.arraycopy(bigOrgIds, 0, nbigOrgIds, 0, qtyArrayLength);
            System.arraycopy(uomCodes, 0, nuomCodes, 0, qtyArrayLength);
            System.arraycopy(splitQuantities, 0, nsplitQuantities, 0,
                             qtyArrayLength);

            for(int i = 0; i < qtyArrayLength; i++)
            {
              nitemIds[i] = itemIds[i];
            }

            nitemIds[qtyArrayLength] = itemIds[qtyArrayLength - 1];
            nbigOrgIds[qtyArrayLength] = nbigOrgIds[qtyArrayLength - 1];
            nuomCodes[qtyArrayLength] = nuomCodes[qtyArrayLength - 1];
            nsplitQuantities[qtyArrayLength] = numFormat.formatNumber(remainderQty);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Item.validateQuantity");

}
            try
            {
              Item.validateQuantity(itemIds, nbigOrgIds, nsplitQuantities, nuomCodes, true);
            } catch (CatalogException e)
            {
              throw new ShoppingCartException("", e);
            }
            
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Item.validateQuantity");
}
          }

          BigDecimal[]  quantitiesArray = new BigDecimal[recordsLength];

          for(int i = 0; i < qtyArrayLength; i++)
          {
            quantitiesArray[i] = bigQuantities[i];
          }

          if(remainderQty != null)
          {
            quantitiesArray[qtyArrayLength] = remainderQty;
          }

          String[]                x_return_status = new String[1];
          int[]                   x_msg_count = new int[1];
          String[]                x_msg_data = new String[1];

          OracleCallableStatement ocs = null;
          BigDecimal              quoteHeaderId = new BigDecimal(cartId);
          BigDecimal              quoteLineId =
            new BigDecimal(scartItem.cartLineId);
          Timestamp lastUpdateDate = Timestamp.valueOf(lastModifiedTimestamp);

          try
          {
            OracleConnection  conn =
              (OracleConnection) TransactionScope.getConnection();

            conn.setAutoCommit(false);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "About to call PL/SQL split api");

}
            StringBuffer  ocsStmt = new StringBuffer(100);
            ocsStmt.append("BEGIN " + IBEUtil.getEnableDebugString() + " IBE_QUOTE_MISC_pvt.Split_Line("
                           + "p_api_version_number => 1.0, "
                           + "p_init_msg_list => FND_API.G_FALSE, "
                           + "p_commit => FND_API.G_FALSE, "
                           + "x_return_status   => :1, "
                           + "x_msg_count       => :2, "
                           + "x_msg_data        => :3, "
                           + "p_quote_header_id => :4, "
                           + "p_qte_line_id => :5, "
                           + "p_quantities => :6, "
                           + "p_last_update_date => :7, "
   + "p_party_id => :8, "
   + "p_cust_account_id => :9, "
   + "p_quote_retrieval_number => :10, "
   + "p_validate_user => FND_API.G_TRUE, "
   + "p_minisite_id  => :11); " + IBEUtil.getDisableDebugString() + "  END;");

            ocs = (OracleCallableStatement) conn.prepareCall(ocsStmt.toString());

            // register types of OUT and IN-OUT, if any
            ocs.registerOutParameter(1, OracleTypes.VARCHAR,0,IBEUtil.RTN_STATUS_MAXLENGTH);
            ocs.registerOutParameter(2, OracleTypes.NUMBER);
            ocs.registerOutParameter(3, OracleTypes.VARCHAR,0,IBEUtil.MSGDATA_MAXLENGTH);
            ocs.registerOutParameter(7, OracleTypes.TIMESTAMP);

            // register IN or IN-OUT params, if any
            ocs.setBigDecimal(4, quoteHeaderId);
            ocs.setBigDecimal(5, quoteLineId);

            ARRAY quantitiesARRAY =
              new ARRAY(GeneralUtil.getArrayDescriptor("JTF_NUMBER_TABLE", conn),
                        conn, quantitiesArray);

            ocs.setARRAY(6, quantitiesARRAY);
            ocs.setTimestamp(7, lastUpdateDate);
    ocs.setBigDecimal(8, RequestCtx.getPartyId());
ocs.setBigDecimal(9, RequestCtx.getAccountId());
ocs.setBigDecimal(10, RequestCtx.getShareeNumber());
ocs.setBigDecimal(11, RequestCtx.getMinisiteId());

            ocs.execute();

            // get OUT and IN-OUT params, if any
            x_return_status[0] = ocs.getString(1);
            x_msg_count[0] = ocs.getInt(2);
            x_msg_data[0] = ocs.getString(3);

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling PL/SQL split api");

}
            if(!FndConstant.getGRetStsSuccess().equals(x_return_status[0]))
            {
              if (ocs != null)
              {
                Timestamp lastUpdateDateFromDB = ocs.getTimestamp(7);
                if ((lastUpdateDateFromDB != null)
                  && (lastUpdateDateFromDB.after(lastUpdateDate)) )
                {
                  reloadFlag = true;
                  throw new ShoppingCartException(x_msg_count[0], (String) null);
                }
              }
              if(x_msg_count[0] > 1)
              {
                if(FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
                {
                  throw ErrorStackUtil.getDBFrameworkException(x_msg_count[0]);
                }
                else
                {
                  throw new QuoteException(x_msg_count[0], (String) null);
                }
              }
              else
              {
                if(FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
                {
                  throw new FrameworkException(0, x_msg_data[0]);
                }
                else
                {
                  throw new QuoteException(x_msg_count[0], (String) null);
                }
              }
            }
          }
          finally
          {
            if(ocs != null)
            {
              ocs.close();

            }
          }
        }
        else
        {
          throw new ShoppingCartException("IBE_SC_INVALID_SPLT_QTYS");
        }
      }
      else
      {
        throw new ShoppingCartException("IBE_SC_SPLT_QTYS_ARY_NULL");
      }
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");

}
  }

  /*
   * returns the privilege type , might be a static string
   *
   * public static String authorizeRetrieveCart()
   * throws FrameworkException, SQLException, ShoppingCartException
   * {
   * return null;
   * }
   * 
   * public static ShoppingCart retrieveCart()
   * throws FrameworkException, SQLException, ShoppingCartException
   * {
   * return null;
   * }
   * 
   */

  /**
   * Saves the header shipment details for the cart.<BR>
   * <BR>
   * Calls saveShippingInformation with saveLineShipments set to false.
   * Please see javadoc for that api for parameter behavior.
   * 
   * @param cartId - the cart id
   * @param lastModifiedTimestamp - the last modified time stamp
   * @param shipmentId - the shipment id (if one exists, else pass null)
   * @param shiptoCustomerAccountId - the ship to customer account id, pass null
   * to default from the sold to.
   * @param shiptoContactPartyId - the ship to contact party id. Pass null if no
   * contact is needed.
   * @param shiptoPartySiteId - the ship to address id.
   * @param shiptoPartySiteType - the ship to address type.
   * @param shippingMethod - the shipping method code.
   * @param requestedDeliveryDate - the requested delivery date in the date format
   * preference of the user.
   * @param shippingInstructions - the shipping instructions (if any, else pass "").
   * @param packingInstructions - the packing instructions (if any, else pass "").
   * 
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveHeaderShipmentDetails(String cartId,
                                        String lastModifiedTimestamp,
                                        String shipmentId,
                                        String shiptoCustomerAccountId,
                                        String shiptoContactPartyId,
                                        String shiptoPartySiteId,
                                        String shiptoPartySiteType,
                                        String shippingMethod,
                                        String requestedDeliveryDate,
                                        String shippingInstructions,
                                        String packingInstructions) throws FrameworkException,
                                        SQLException, ShoppingCartException,
                                        QuoteException
  {
    String  METHOD = "saveHeaderShipmentDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    if(cartId == null)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId is null");

}
      throw new ShoppingCartException("IBE_CART_IS_INVALID");
    }

    this.cartId = cartId;
    this.shiptoPartySiteId = shiptoPartySiteId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
    this.shipmentId = shipmentId;
    this.shiptoContactPartyId = shiptoContactPartyId;
    this.shiptoPartySiteId = shiptoPartySiteId;
    this.shiptoPartySiteType = shiptoPartySiteType;
    this.shiptoCustomerAccountId = shiptoCustomerAccountId;
    this.shippingMethod = shippingMethod;
    this.requestedDeliveryDate = requestedDeliveryDate;
    this.shippingInstructions = shippingInstructions;
    this.packingInstructions = packingInstructions;

    saveShippingInformation(false);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /*
   * public void saveHeaderAndLineShipmentDetails(String cartId,
   * String lastModifiedTimestamp, ShoppingCartItem[] shopCartItems,
   * String shipmentId, String shiptoCustomerAccountId,
   * String shiptoContactPartyId, String shiptoPartySiteId,
   * String shippingMethod, String requestedDeliveryDate,
   * String shippingInstructions,
   * String packingInstructions) throws FrameworkException,
   * SQLException,
   * ShoppingCartException
   * {
   * }
   */

  /**
   * Saves shipment details at item level (line level). Prior to calling this
   * method, the setShoppingCartItems method must be called to set the items in
   * the cart. The ShoppingCartItem will need to contain the shipping related
   * values which need to be modified.<BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <LI>shopCartItems array via setShoppingCartItems method each with any of the following fields: <BR>
   shopCartItem.shiptoCustomerAccountId,<BR>
                              shopCartItem.shiptoContactPartyId,<BR>
                              shopCartItem.shiptoPartySiteId,<BR>
                              shopCartItem.shiptoPartySiteType,<BR>
                              shopCartItem.shipmentId,<BR>
                              shopCartItem.requestedDeliveryDate,<BR>
                              shopCartItem.shippingMethod,<BR>
                              shopCartItem.shippingInstructions,<BR>
                              shopCartItem.packingInstructions
<LI>for line records, the operation code for the shipment record is always update since Order Capture creates a shipment record with the line; therefore, a line shipmentId is always required
<LI>if a shiptoCustomerAccountId and a shiptoContactPartyId are both specified, then a shiptoPartySiteId is required
<LI>setting shiptoContactPartyId to null will remove it and if shiptoPartySiteType is "contact", it will remove the shiptoPartySiteId as well
(since the address belonged to the contact and the contact is being removed, his/her address should no longer be used)
<LI>if shiptoCustomerAccountId is set to "", it will be removed; if it is set to null, it will be removed along with the shiptoPartySiteId and the shiptoContactPartyId<BR>
<BR>
   * Internally, this api sets the headerRec, controlRec (to do pricing), lineShipmentRec and calls Quote.save
   * Please see the comments for Quote.save for more information.

   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveLineShipmentDetails()
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    String      METHOD = "saveLineShipmentDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    saveLineBillingShippingDetails(true);
  }

  /**
   * Saves Billing information for individual lines.<BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method
   * <LI>soldToAccountId via ShoppingCart.setSoldToCustomerAccountId api.
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <LI>shopCartItems array via setShoppingCartItems method each with any of the following fields: <BR>
   shopCartItem.billtoCustomerAccountId,<BR>
                              shopCartItem.billtoContactPartyId,<BR>
                              shopCartItem.billtoPartySiteId,<BR>
                              shopCartItem.billtoPartySiteType
<LI>for line records, the operation code is always update since the billing information is stored in the aso_quote_lines_all table itself
<LI>if a billtoCustomerAccountId and a billtoContactPartyId are both specified, then a billtoPartySiteId is required
<LI>setting billtoContactPartyId to null will remove it and if billtoPartySiteType is "contact", it will remove the billtoPartySiteId as well
(since the address belonged to the contact and the contact is being removed, his/her address should no longer be used)
<LI>if billtoCustomerAccountId is set to "", it will be removed; if it is set to null, it will be removed along with the billtoPartySiteId and the billtoContactPartyId<BR>
<BR>
   * Internally, this api sets the headerRec, controlRec (to do pricing), lineRec and calls Quote.save
   * Please see the comments for Quote.save for more information.
  *
  */
  public void saveLineBillingDetails()
          throws FrameworkException, SQLException, QuoteException, 
                 ShoppingCartException
  {
    String METHOD = "saveLineBillingDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called");
}
    saveLineBillingShippingDetails(false);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "finished");    
}
  }

  /** 
   * Saves the line billing information. The method also saves the line shipping
   * information if the flag doShipping is set to true.
   *
   * @param doShipping The flag to indicate whether to save the line shipping information.
   * 
   * @throws FrameworkException If there is a framework layer error
   * @throws SQLException If there is a database error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of
   * of the error
   * @rep:displayname Save Line Billing Shipping Details
   */
  public void saveLineBillingShippingDetails(boolean doShipping)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    String      METHOD = "saveLineBillingShippingDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, " doShipping: " + doShipping);
}
    try
    {
      setupControlRecord(YES, YES, true);

      BigDecimal  quoteHeaderId = new BigDecimal(cartId);

      setupHeaderRecord(quoteHeaderId);

      // this needs to be done for either billing or shipping
      setupLineRecords(quoteHeaderId, UPDATE_OPCODE, !doShipping);

      if (doShipping) {
        // this only needs to be done for shipping
        setupLineShipmentRecords(customerAccountId);
      }

      BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
      BigDecimal  partyId = null;
      BigDecimal  acctId = null;

      if(shareeNumber != null)
      {
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }
      try
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.save");
}
        save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
             true, false, false, false, false, false, false, false, false,
             false, true, false, false, false, false, false, false);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.save");
}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "Exception occured while cons BigDecimal: "
                  + e.getMessage());
      IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "Exception occured while cons BigDecimal: "
                  + e.getMessage());
      IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Saves the shipping information in the cart. The method expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method.
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp.
   * <LI>For header shipping info, any of the following fields:
   * customerAccountId, shiptoCustomerAccountId,shiptoContactPartyId, shiptoPartySiteId,
   * shiptoPartySiteType, shipmentId,requestedDeliveryDate, shippingMethod,shippingInstructions, packingInstructions
   * <LI>For item shipping info, shopCartItems array via setShoppingCartItems method each with any of the following fields:
   * shopCartItem.shiptoCustomerAccountId,shopCartItem.shiptoContactPartyId,shopCartItem.shiptoPartySiteId,
   * shopCartItem.shiptoPartySiteType,shopCartItem.shipmentId,shopCartItem.requestedDeliveryDate,
   * shopCartItem.shippingMethod,shopCartItem.shippingInstructions,shopCartItem.packingInstructions
   * <LI>For the header shipment record, if the unique identifier of shipment record is null or "",
   * a new shipment record is created; otherwise, the record indicated by the unique identifier
   * is updated(a line shipmentId is always required because a shipment is created as soon as a cart is created)
   * <LI>If a shiptoCustomerAccountId and a shiptoContactPartyId are both specified, then a shiptoPartySiteId
   * is required.
   * <LI>Setting shiptoContactPartyId to null removes it and if shiptoPartySiteType is "CONTACT", it removes
   * the shiptoPartySiteId as well (since the address belonged to the contact and the contact is being removed,
   * his/her address should no longer be used).
   * <LI>If a shiptoCustomerAccountId is specified for the cart level, and it is the same as the soldtoCustomerAccountId,
   * the shiptoCustomerAccountId will be removed (this serves as recommended performance enhancement as
   * the general behavior for this column is that a blank will be defaulted to the soldtoCustomerAccountId
   * anyways; Please note that this does not apply to line level shiptoCustomerAccountId).
   * <LI>If shiptoCustomerAccountId is set to "", it will be removed; if it is set to null, it is removed
   * along with the shiptoPartySiteId and the shiptoContactPartyId

   * @param saveLineShipments Indicates if shipping information for items is being specified.
   * @throws FrameworkException If there is a framework layer error
   * @throws SQLException If there is a database error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of
   * of the error.
   * @rep:displayname Save Shipping Information
   */
  public void saveShippingInformation(boolean saveLineShipments)
          throws FrameworkException, SQLException, QuoteException, 
                 ShoppingCartException
  {
    String  METHOD = "saveShippingInformation";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    logInputVariables(METHOD);

    setupControlRecord(YES, YES, true);

    try
    {
      if((headerShipmentRec == null) || (headerShipmentRec[0] == null))
      {
        headerShipmentRec =
          new oracle.apps.ibe.shoppingcart.quote.ShipmentRecord[1];
        headerShipmentRec[0] =
          new oracle.apps.ibe.shoppingcart.quote.ShipmentRecord();
      }

      BigDecimal  shoppingCartId = new BigDecimal(cartId);

      setupShipmentRecord(headerShipmentRec[0], shoppingCartId,
                          customerAccountId, shiptoCustomerAccountId,
                          shiptoContactPartyId, shiptoPartySiteId,
                          shiptoPartySiteType, shipmentId,
                          requestedDeliveryDate, shippingMethod,
                          shippingInstructions, packingInstructions, true);
      setupHeaderRecord(shoppingCartId);

      if((shipmentId == null) || (shipmentId.equals(EMPTY_STRING)))
      {
        forceTimestampValidation();

      }

      if((saveLineShipments) && (shopCartItems != null)
              && (shopCartItems.length > 0))
      {
        setupLineShipmentRecords(customerAccountId);
      }

      BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
      BigDecimal  partyId = null;
      BigDecimal  acctId = null;

      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }

      try
      {
        if((saveLineShipments) && (lineShipmentRec != null)
                && (lineShipmentRec.length > 0))
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLing Quote.save with line ship rcrds");
}
          save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
               false, false, false, false, false, false, false, false, false,
               true, true, false, false, false, false, false, false);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.save");
}
        }
        else
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLing Quote.save, hdr ship rcrd only");
}
          save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
               false, false, false, false, false, false, false, false, false,
               true, false, false, false, false, false, false, false);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.save");
}
        }
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED");
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED");
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }
/** savePaymentInformation #1 - calls #3 with calcTaxAndReprice set to true and the retrieval number from the cookie  */
  public void savePaymentInformation(boolean savePaymentOnly)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    // by default, the original signature repriced the cart & got the sharee number from the cookie
    savePaymentInformation(savePaymentOnly, true, getRetrievalNumberString());
  }
/** savePaymentInformation #2 - calls #3 with the retrieval number from the cookie */
  public void savePaymentInformation(boolean savePaymentOnly, boolean calcTaxAndReprice)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    // by default, the original signature got the sharee number from the cookie
    savePaymentInformation(savePaymentOnly, calcTaxAndReprice, getRetrievalNumberString());
  }

  /**
   * savePaymentInformation #3 (NEW BASE): Saves payment, billing,and tax information in the cart to the database. <BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method<BR>
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <BR>
   * For PAYMENT information:
   * <LI>poNumber (optional) - is saved indepenent of the payment type
   * <LI>paymentTermId - if specified, is set to the minisite setting
   * <LI>paymentNumber (for the CHECK_PAYMENT type)
   * <LI>paymentType:
   * <UL>
   * <LI>FAX_CC_PAYMENT,INVOICE_PAYMENT,CASH_PAYMENT: clears out checknum & credit card fields
   * <LI>CC_PAYMENT: gets values from "cc" (CCPayment) object
   * <LI>CHECK_PAYMENT: clears out credit card fields, saves paymentNumber as the check number
   * </UL>
   * <BR>
   * For TAX information:
   * <LI>taxable flag, taxExemptReason, and taxExemptNumber<BR>
   * <BR>
   * For BILLING information:
   * <LI>for header shipping info, any of the following fields:<BR>
  billtoCustomerAccountId,billtoContactPartyId, <BR>
  billtoPartySiteId,billtoPartySiteType
<LI>if a billtoCustomerAccountId and a billtoContactPartyId are both specified, then a billtoPartySiteId is required
<LI>setting billtoContactPartyId to null will remove it and if billtoPartySiteType is "contact", it will remove the billtoPartySiteId as well
(since the address belonged to the contact and the contact is being removed, his/her address should no longer be used)
<LI>if a billtoCustomerAccountId is specified, and it is the same as the soldtoCustomerAccountId, the billtoCustomerAccountId will be removed
(this serves as recommended performance enhancement as the general behavior for this column is that a blank will be defaulted to the soldtoCustomerAccountId anyways;
note that this does not apply to line level shiptoCustomerAccountId)
<LI>if billtoCustomerAccountId is set to "", it will be removed; if it is set to null, it will be removed along with the billtoPartySiteId and the billtoContactPartyId
   * <BR><BR>
   * A note regarding the "primary key fields" for payment and tax records:
   * <LI>for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is set,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber or any other payment related fields
   * would not be saved or updated;
   * for the taxDetailId, an NOOP_STRING value would mean that the taxable flag, taxExemptReason,
   * and taxExemptNumber values would not be saved or updated.  This is intended
   * to save the jdbc preparation calls in the Quote.java object, throughput to the database,
   * and update operations in the plsql layer.
   * <LI>Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   * <BR>
   * NOTE: the savePaymentOnly flag will still override any settings of number values
   * (i.e. if taxDetailId is set to a number, but savePaymentOnly is set to true, the tax
   * info will not be saved.)
   *
   * @param savePaymentOnly (formerly "saveLinePayments" which was never supported)
   *  - true indicates that payment information only (and not billing or tax info) is to be saved.
   * @param calcTaxAndReprice boolean to re-price the cart or not
   * @param retrievalNumber in the event that a recipient is updating the cart,
   * his/her unique sharee number must be passed for validation
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void savePaymentInformation(boolean savePaymentOnly, boolean calcTaxAndReprice, String retrievalNumber)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    String  METHOD = "savePaymentInformation";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "  savePaymentOnly  : " + savePaymentOnly);
    IBEUtil.log(CLASS, METHOD, "  calcTaxAndReprice: " + calcTaxAndReprice);
    IBEUtil.log(CLASS, METHOD, "  retrievalNumber  : " + retrievalNumber);
}
    logInputVariables(METHOD);

    boolean savePaymentFlag = true;
    if (NOOP_STRING.equals(this.paymentId)) {
      savePaymentFlag = false;
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "  savePaymentFlag is: " + savePaymentFlag);

}
    boolean saveTaxDetailsFlag = true;
    if (NOOP_STRING.equals(this.taxDetailId) || savePaymentOnly) {
      saveTaxDetailsFlag = false;
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "  saveTaxDetailsFlag is: " + saveTaxDetailsFlag);

}
    try
    {
      BigDecimal  quoteHeaderId = new BigDecimal(cartId);
      BigDecimal  shareeNumber = makeRetrievalNumBigDecimal(retrievalNumber);

      setupHeaderRecord(quoteHeaderId, shareeNumber);
      if (calcTaxAndReprice) {
        setupControlRecord(YES, NO, true);
      } else {
        setupControlRecord(NO, NO, false);
      }
   /*********************** TAX INFO ******************************************/
      if (saveTaxDetailsFlag) {
      // skip tax info if we're only saving payment info
      oracle.apps.ibe.shoppingcart.quote.TaxDetailRecord  taxRecord = null;

      if((headerTaxDetailRec == null) || (headerTaxDetailRec[0] == null))
      {
        taxRecord = new oracle.apps.ibe.shoppingcart.quote.TaxDetailRecord();
        headerTaxDetailRec =
          new oracle.apps.ibe.shoppingcart.quote.TaxDetailRecord[1];
        headerTaxDetailRec[0] = taxRecord;
      }
      else
      {
        taxRecord = headerTaxDetailRec[0];
      }

      taxRecord.quote_header_id = quoteHeaderId;

      if((taxDetailId == null) || EMPTY_STRING.equals(taxDetailId))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Creating tax detail record");
}
        taxRecord.operation_code = CREATE_OPCODE;
        taxRecord.quote_shipment_id = getHeaderShipmentID(quoteHeaderId);
      }
      else// if (!NOOP_CODE.equals(taxDetailId))
      //(don't really need to do comparison here since this is in an if that's determined by this comparison earlier
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Updating tax detail record");
}
        taxRecord.tax_detail_id = new BigDecimal(taxDetailId);
        taxRecord.quote_shipment_id = getHeaderShipmentID(quoteHeaderId);
        taxRecord.operation_code = UPDATE_OPCODE;
      }

      taxRecord.tax_exempt_flag = taxExemptFlag;
      if(taxExemptFlag.equals("E"))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting tax exemption");
}
        taxRecord.tax_exempt_reason_code = taxReasonCode;
        taxRecord.tax_exempt_number = taxCertificateNumber;
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Resetting tax exemption");
}
        taxRecord.tax_exempt_reason_code = null;
        taxRecord.tax_exempt_number = null;
      }
      } // end if savePaymentOnly

   /*********************** PAYMENT INFO **************************************/
      if (savePaymentFlag) {
      oracle.apps.ibe.shoppingcart.quote.PaymentRecord  paymentRecord = null;

      if((headerPaymentRec == null) || (headerPaymentRec[0] == null))
      {
        paymentRecord =
          new oracle.apps.ibe.shoppingcart.quote.PaymentRecord();
        headerPaymentRec =
          new oracle.apps.ibe.shoppingcart.quote.PaymentRecord[1];
        headerPaymentRec[0] = paymentRecord;
      }
      else
      {
        paymentRecord = headerPaymentRec[0];
      }

      paymentRecord.quote_header_id = quoteHeaderId;

      String  paymentTermId =
        StoreMinisite.getMinisiteAttribute("IBE_DEFAULT_PAYMENT_TERM_ID");

      if(paymentTermId != null)
      {
        paymentRecord.payment_term_id = new BigDecimal(paymentTermId);
      }

      if((paymentId == null) || EMPTY_STRING.equals(paymentId))
      {
        paymentRecord.operation_code = CREATE_OPCODE;
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Pmt rcrd creation");
}
      }
      else //if (!NOOP_CODE.equals(paymentId))
      //(don't really need to do comparison here since this is in an if that's determined by this comparison earlier
      {
        paymentRecord.payment_id = new BigDecimal(paymentId);
        paymentRecord.operation_code = UPDATE_OPCODE;
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Pmt rcrd update fr paymentId = " + paymentId);
}
      }
      // set the po number (which is now independent of payment type)
      paymentRecord.cust_po_number = poNumber;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting to update poNumber to be = " + poNumber);
      IBEUtil.log(CLASS, METHOD, "Payment type is " + paymentType);
}
      if(FAX_CC_PAYMENT.equals(paymentType))
      {
        paymentRecord.payment_type_code = CC_PAYMENT;
        paymentRecord.payment_ref_number = null;
        paymentRecord.credit_card_code = null;
        paymentRecord.credit_card_expiration_date = null;
        paymentRecord.credit_card_holder_name = null;
      }
      else if(PO_PAYMENT.equals(paymentType))
      {
//        paymentRecord.payment_amount = BigDecimal.valueOf(0);
        paymentRecord.payment_ref_number = paymentNumber;
        paymentRecord.payment_type_code = PO_PAYMENT;
        paymentRecord.credit_card_code = null;
        paymentRecord.credit_card_expiration_date = null;
        paymentRecord.credit_card_holder_name = null;
      }
      else if(FAX_PO_PAYMENT.equals(paymentType))
      {
//        paymentRecord.payment_amount = BigDecimal.valueOf(0);
        paymentRecord.payment_ref_number = null;
        paymentRecord.payment_type_code = PO_PAYMENT;
        paymentRecord.credit_card_code = null;
        paymentRecord.credit_card_expiration_date = null;
        paymentRecord.credit_card_holder_name = null;
      }
      else if(CC_PAYMENT.equals(paymentType))
      {
        if(cc == null)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CC is null");
}
          // new protocol allows for saving of payment info  w/ CC option set
          // but not necessarily needing to save the CC info
          // (case: saving PO number update, but not changing the existing CC)
          //throw new ShoppingCartException("IBE_MISSING_CC");
        }
        else
        {
          paymentRecord.payment_type_code = CC_PAYMENT;

          if((cc.credit_card_holder_name != null)
                  &&!cc.credit_card_holder_name.equals(EMPTY_STRING))
          {
            paymentRecord.credit_card_holder_name =
              cc.credit_card_holder_name;
          }
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "CC holder name is " + cc.credit_card_holder_name);

}
          if(!cc.credit_card_num.equals(EMPTY_STRING))
          {
            paymentRecord.payment_ref_number = cc.credit_card_num;
          }

          if(!cc.credit_card_type_code.equals(EMPTY_STRING))
          {
            paymentRecord.credit_card_code = cc.credit_card_type_code;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "CC type is " + cc.credit_card_type_code);
}
          }

          if(cc.exp_date != null)
          {
            paymentRecord.credit_card_expiration_date =
              new java.sql.Timestamp(cc.exp_date.getTime());
          }
        }
      }
      else if((INVOICE_PAYMENT.equals(paymentType))
              || (CASH_PAYMENT.equals(paymentType)))
      {
        if(INVOICE_PAYMENT.equals(paymentType))
        {
          paymentRecord.payment_type_code = null;
        }
        else
        {
          paymentRecord.payment_type_code = CASH_PAYMENT;
        }
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Resetting pmt fields for cash/invoice pmt option");
}
        paymentRecord.payment_option = null;
        paymentRecord.payment_ref_number = null;
        paymentRecord.credit_card_expiration_date = null;
        paymentRecord.credit_card_code = null;
        paymentRecord.credit_card_holder_name = null;
//        paymentRecord.payment_amount = BigDecimal.valueOf(0);
      }
      else if(CHECK_PAYMENT.equals(paymentType))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Resetting pmt fields for check pmt option");
}
//        paymentRecord.payment_amount = BigDecimal.valueOf(0);
        paymentRecord.payment_ref_number = paymentNumber;
        paymentRecord.payment_type_code = CHECK_PAYMENT;
        paymentRecord.credit_card_code = null;
        paymentRecord.credit_card_expiration_date = null;
        paymentRecord.credit_card_holder_name = null;
      }
      else
      {
        throw new ShoppingCartException("IBE_PRMT_NO_PMT_SELECTED");
      }
      } // end if (savePaymentFlag)
   /*********************** BILLING INFO **************************************/
      if (!savePaymentOnly)
      {

      if((paymentId == null) || paymentId.equals(EMPTY_STRING))
      {
        forceTimestampValidation();
      }

      if((billtoPartySiteId != null) &&!billtoPartySiteId.equals(""))
      {
        headerRec.invoice_to_party_site_id =
          new BigDecimal(billtoPartySiteId);
      }
      else if((billtoContactPartyId != null)
              && (billtoCustomerAccountId != null))
      {
        throw new ShoppingCartException("IBE_SC_NO_BILL_ADDRS");
      }

      if((billtoContactPartyId != null) &&!billtoContactPartyId.equals(""))
      {
        headerRec.invoice_to_party_id = new BigDecimal(billtoContactPartyId);
      }
      else
      {
        headerRec.invoice_to_party_id = null;
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Nulling invoice_to_party_id");
}
        if(billtoPartySiteType.equals("contact"))
        {
          headerRec.invoice_to_party_site_id = null;
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Nulling addrs bcos existing addrs is of type contact");
}
        }
      }

      if((billtoCustomerAccountId != null)
              && (!billtoCustomerAccountId.equals(""))
              && (!billtoCustomerAccountId.equals(customerAccountId)))
      {
        headerRec.invoice_to_cust_account_id =
          new BigDecimal(billtoCustomerAccountId);

      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Nulling invoice_to_cust_account_id");
}
        headerRec.invoice_to_cust_account_id = null;

        if(billtoCustomerAccountId == null)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Nulling addrs and contact");
}
          headerRec.invoice_to_party_site_id = null;
          headerRec.invoice_to_party_id = null;
        }
      }
      } // end if !savePaymentOnly

      BigDecimal  custAcctId = null;
      BigDecimal  partyId = null;
      int saveType = this.saveType;
      // sharee number had to be declared above the call to setupHeaderRecord
      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
        custAcctId = RequestCtx.getAccountId();
        partyId = RequestCtx.getPartyId();
      }

      // save payment, header only
      try
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling quote.save");
}
        // set saveHeaderPayment to true always
        // set saveHeaderTaxDetail to true only if we're not only doing payment
        save(partyId, custAcctId, shareeNumber, Quote.SEPARATE_LINES, false,
             false, false, false, false, false, false, false, savePaymentFlag, false,
             false, false, false, false, saveTaxDetailsFlag, false, false, false,saveType);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling save");
}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   *
   * @param cartId
   * @param paymentId
   * @param taxDetailId
   * @param lastModifiedTimestamp
   * @param billtoCustomerAccountId
   * @param billtoContactPartyId
   * @param billtoPartySiteId
   * @param billtoPartySiteType
   * 
   * @throws ShoppingCartException
   */
  private void setupShopCartPaymentInfo(String cartId, String paymentId,
                                        String taxDetailId, boolean taxable,
                                        String taxExemptReason, String taxExemptNumber,
                                        String lastModifiedTimestamp,
                                        String billtoCustomerAccountId,
                                        String billtoContactPartyId,
                                        String billtoPartySiteId,
                                        String billtoPartySiteType,
                                        String custPoNumber) throws ShoppingCartException
  {
    String  METHOD = "setupShopCartPaymentInfo";

    if(cartId == null)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart id is NULL");

}
      throw new ShoppingCartException("IBE_CART_IS_INVALID");
    }

    this.cartId = cartId;
    this.taxDetailId = taxDetailId;
    this.paymentId = paymentId;
    this.lastModifiedTimestamp = lastModifiedTimestamp;
    this.billtoCustomerAccountId = billtoCustomerAccountId;
    this.billtoContactPartyId = billtoContactPartyId;
    this.billtoPartySiteId = billtoPartySiteId;
    this.billtoPartySiteType = billtoPartySiteType;
    this.poNumber = custPoNumber;

    // only do this validation if user is asking to be tax exempt
    // AND we are being asked to save (if not saving, the reason & number
    // are not expected to be passed in; even if exempt)
    if(!taxable && !NOOP_STRING.equals(taxDetailId))
    {
      checkTaxation(taxExemptReason, taxExemptNumber);
    }
    else if (taxable)
    {
      this.taxExemptFlag = "S";
    }
  }

  /**
   * Method declaration
   *
   *
   * @param taxExemptReason
   * @param taxExemptNumber
   * 
   * @throws ShoppingCartException
   */
  private final void checkTaxation(String taxExemptReason, String taxExemptNumber) 
          throws ShoppingCartException
  {
    String  METHOD = "checkTaxation";

    this.taxExemptFlag = "E";

    if((taxExemptReason == null) || (taxExemptReason.equals(EMPTY_STRING)))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, " taxExemptReason is " + taxExemptReason);

}
      throw new ShoppingCartException("IBE_SC_MISS_TAX_REASON");
    }

    this.taxReasonCode = taxExemptReason;
    this.taxCertificateNumber = taxExemptNumber;
  }
  /**
   * Saves the payment information when the payment option is a credit card.
   * The cc parameter object will need to contain the number, code, expiry time stamp.
   * @deprecated
   */
  public void saveCCPayment(String cartId, String paymentId,
                            String lastModifiedTimestamp,
                            String billtoCustomerAccountId,
                            String billtoContactPartyId,
                            String billtoPartySiteId,
                            String billtoPartySiteType, CCPayment cc,
                            String taxDetailId, boolean taxable,
                            String taxExemptReason,
                            String taxExemptNumber) throws FrameworkException,
                            SQLException, QuoteException, 
                            ShoppingCartException
  {
    saveCCPayment( cartId,  paymentId,
                             lastModifiedTimestamp,
                             billtoCustomerAccountId,
                             billtoContactPartyId,
                             billtoPartySiteId,
                             billtoPartySiteType,  cc,
                             taxDetailId,  taxable,
                             taxExemptReason,
                             taxExemptNumber, null);
  }
  /**
   * Saves the payment information when the payment option is a credit card.
   * The cc parameter object will need to contain the number, code, expiry time stamp.
   *
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber as well as the Credit Card
   * info would not be saved or updated; for the taxDetailId, an NOOP_STRING value
   * would mean that the taxable flag, taxExemptReason, and taxExemptNumber values
   * would not be saved or updated.  This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database, and update
   * operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   * 
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record if one exists
   * @param lastModifiedTimestamp - the time stamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address type
   * @param cc- the credit card object
   * @param taxDetailId - the id of the tax detail record if one exists
   * @param taxable - specifes if the order is taxable
   * @param taxExemptReason - the tax exempt reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is tax exempt
   * @param poNumber - po number   
   * 
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveCCPayment(String cartId, String paymentId,
                            String lastModifiedTimestamp, 
                            String billtoCustomerAccountId, 
                            String billtoContactPartyId, 
                            String billtoPartySiteId, 
                            String billtoPartySiteType, CCPayment cc, 
                            String taxDetailId, boolean taxable, 
                            String taxExemptReason,
                            String taxExemptNumber,
                            String poNumber) throws FrameworkException,
                            SQLException, QuoteException, 
                            ShoppingCartException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId, 
                             billtoContactPartyId, billtoPartySiteId, 
                             billtoPartySiteType,poNumber);

    this.cc = cc;
    this.paymentType = CC_PAYMENT;

    savePaymentInformation(false);
  }

  /**
   * Saves the payment information when the payment option is PO.
   *
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber would not be saved or updated;
   * for the taxDetailId, an NOOP_STRING value would mean that the taxable flag, taxExemptReason,
   * and taxExemptNumber values would not be saved or updated.  This is intended
   * to save the jdbc preparation calls in the Quote.java object, throughput to the database,
   * and update operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   *
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record , if one exists
   * @param lastModifiedTimestamp - the timestamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address id
   * @param poNumber - the purchase order number
   * @param taxDetailId - the id of the tax detail record
   * @param taxable - flag to indicate if the order is taxable
   * @param taxExemptReason - the tax exemption reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is not taxable
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void savePOPayment(String cartId, String paymentId,
                            String lastModifiedTimestamp,
                            String billtoCustomerAccountId,
                            String billtoContactPartyId,
                            String billtoPartySiteId,
                            String billtoPartySiteType, String poNumber,
                            String taxDetailId, boolean taxable,
                            String taxExemptReason,
                            String taxExemptNumber) throws FrameworkException,
                            SQLException, ShoppingCartException,
                            QuoteException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType, poNumber);

//    this.paymentType = PO_PAYMENT;
//    this.paymentNumber = poNumber;
//    this.poNumber = poNumber; // now this gets done by the setupShopCartPaymentInfo api

    savePaymentInformation(false);
  }
  /**
   * Saves the payment information when the payment option is Check.
   * @deprecated
   */
  public void saveCheckPayment(String cartId, String paymentId,
                               String lastModifiedTimestamp,
                               String billtoCustomerAccountId,
                               String billtoContactPartyId,
                               String billtoPartySiteId,
                               String billtoPartySiteType,
                               String checkNumber, String taxDetailId,
                               boolean taxable, String taxExemptReason,
                               String taxExemptNumber) throws FrameworkException,
                               SQLException, ShoppingCartException, 
                               QuoteException
  {
    saveCheckPayment( cartId,  paymentId,
                                lastModifiedTimestamp,
                                billtoCustomerAccountId,
                                billtoContactPartyId,
                                billtoPartySiteId,
                                billtoPartySiteType,
                                checkNumber,  taxDetailId,
                                taxable,  taxExemptReason,
                                taxExemptNumber, null);
  }
  /**
   * Saves the payment information when the payment option is Check.
   * 
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber as well as the Check number
   * info would not be saved or updated; for the taxDetailId, an NOOP_STRING value
   * would mean that the taxable flag, taxExemptReason, and taxExemptNumber values
   * would not be saved or updated.  This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database, and update
   * operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   *
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record , if one exists
   * @param lastModifiedTimestamp - the timestamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address id
   * @param checkNumber - the check number
   * @param taxDetailId - the id of the tax detail record
   * @param taxable - flag to indicate if the order is taxable
   * @param taxExemptReason - the tax exemption reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is not taxable
   * @param poNumber - po number
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveCheckPayment(String cartId, String paymentId,
                               String lastModifiedTimestamp,
                               String billtoCustomerAccountId,
                               String billtoContactPartyId,
                               String billtoPartySiteId,
                               String billtoPartySiteType,
                               String checkNumber, String taxDetailId,
                               boolean taxable, String taxExemptReason,
                               String taxExemptNumber, String poNumber) throws FrameworkException,
                               SQLException, ShoppingCartException,
                               QuoteException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType,poNumber);

    this.paymentType = CHECK_PAYMENT;
    this.paymentNumber = checkNumber;

    savePaymentInformation(false);
  }

  /**
   * Saves the payment information at the cart level (header level) when the
   * payment option is faxed credit card
   * @deprecated
   */
  public void saveFaxCCPayment(String cartId, String paymentId,
                               String lastModifiedTimestamp,
                               String billtoCustomerAccountId,
                               String billtoContactPartyId,
                               String billtoPartySiteId,
                               String billtoPartySiteType,
                               String taxDetailId, boolean taxable,
                               String taxExemptReason,
                               String taxExemptNumber) throws FrameworkException,
                               SQLException, ShoppingCartException,
                               QuoteException
  {
    saveFaxCCPayment( cartId,  paymentId,
                                lastModifiedTimestamp,
                                billtoCustomerAccountId,
                                billtoContactPartyId,
                                billtoPartySiteId,
                                billtoPartySiteType,
                                taxDetailId,  taxable,
                                taxExemptReason,
                                taxExemptNumber, null);
  }
  /**
   * Saves the payment information at the cart level (header level) when the
   * payment option is faxed credit card
   *
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber as well as the FaxCC
   * info would not be saved or updated; for the taxDetailId, an NOOP_STRING value
   * would mean that the taxable flag, taxExemptReason, and taxExemptNumber values
   * would not be saved or updated.  This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database, and update
   * operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   *
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record , if one exists
   * @param lastModifiedTimestamp - the timestamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address id
   * @param taxDetailId - the id of the tax detail record
   * @param taxable - flag to indicate if the order is taxable
   * @param taxExemptReason - the tax exemption reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is not taxable
   * @param poNumber - po number   
   * 
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveFaxCCPayment(String cartId, String paymentId, 
                               String lastModifiedTimestamp, 
                               String billtoCustomerAccountId, 
                               String billtoContactPartyId, 
                               String billtoPartySiteId, 
                               String billtoPartySiteType, 
                               String taxDetailId, boolean taxable, 
                               String taxExemptReason, 
                               String taxExemptNumber,
                               String poNumber) throws FrameworkException,
                               SQLException, ShoppingCartException, 
                               QuoteException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType, poNumber);

    this.paymentType = FAX_CC_PAYMENT;
    this.paymentNumber = "";

    savePaymentInformation(false);
  }

  /**
   * Saves payment information at the cart level (header level) when the payment
   * option is invoice.
   * @deprecated
   */
  public void saveInvoicePayment(String cartId, String paymentId,
                                 String lastModifiedTimestamp,
                                 String billtoCustomerAccountId,
                                 String billtoContactPartyId,
                                 String billtoPartySiteId,
                                 String billtoPartySiteType,
                                 String taxDetailId, boolean taxable,
                                 String taxExemptReason,
                                 String taxExemptNumber) throws FrameworkException,
                                 SQLException, ShoppingCartException, 
                                 QuoteException
  {
    saveInvoicePayment( cartId,  paymentId,
                                  lastModifiedTimestamp,
                                  billtoCustomerAccountId,
                                  billtoContactPartyId,
                                  billtoPartySiteId,
                                  billtoPartySiteType,
                                  taxDetailId,  taxable,
                                  taxExemptReason,
                                  taxExemptNumber,
                                  null);
  }

  /**
   * Saves payment information at the cart level (header level) when the payment
   * option is invoice.
   *
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber as well as the Invoice 
   * info would not be saved or updated; for the taxDetailId, an NOOP_STRING value
   * would mean that the taxable flag, taxExemptReason, and taxExemptNumber values
   * would not be saved or updated.  This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database, and update
   * operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   *
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record , if one exists
   * @param lastModifiedTimestamp - the timestamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address id
   * @param taxDetailId - the id of the tax detail record
   * @param taxable - flag to indicate if the order is taxable
   * @param taxExemptReason - the tax exemption reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is not taxable
   * @param poNumber - po number
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveInvoicePayment(String cartId, String paymentId,
                                 String lastModifiedTimestamp, 
                                 String billtoCustomerAccountId, 
                                 String billtoContactPartyId, 
                                 String billtoPartySiteId, 
                                 String billtoPartySiteType, 
                                 String taxDetailId, boolean taxable, 
                                 String taxExemptReason, 
                                 String taxExemptNumber,
                                 String poNumber) throws FrameworkException,
                                 SQLException, ShoppingCartException, 
                                 QuoteException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType,poNumber);

    this.paymentType = INVOICE_PAYMENT;
    this.paymentNumber = null;

    savePaymentInformation(false);
  }

  /**
   * Saves payment information at the cart level (header level) when the payment
   * option is Cash.
   * @deprecated
   */
 public void saveCashPayment(String cartId, String paymentId,
                              String lastModifiedTimestamp,
                              String billtoCustomerAccountId,
                              String billtoContactPartyId,
                              String billtoPartySiteId,
                              String billtoPartySiteType, String taxDetailId,
                              boolean taxable, String taxExemptReason,
                              String taxExemptNumber) throws FrameworkException,
                              SQLException, ShoppingCartException,
                              QuoteException
  {
    saveCashPayment(cartId, paymentId,
                               lastModifiedTimestamp,
                               billtoCustomerAccountId,
                               billtoContactPartyId,
                               billtoPartySiteId,
                               billtoPartySiteType,  taxDetailId,
                               taxable,  taxExemptReason,
                               taxExemptNumber, null);

  }

  /**
   * Saves payment information at the cart level (header level) when the payment
   * option is Cash.
   *
   * New Behavior: for paymentId and taxDetailId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * For the paymentId, this would mean that the poNumber as well as the Cash
   * info would not be saved or updated; for the taxDetailId, an NOOP_STRING value
   * would mean that the taxable flag, taxExemptReason, and taxExemptNumber values
   * would not be saved or updated.  This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database, and update
   * operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   *
   * @param cartId - the cart id
   * @param paymentId - the id of the payment record , if one exists
   * @param lastModifiedTimestamp - the timestamp when the cart was last modified
   * @param billtoCustomerAccountId - the bill to customer account id
   * @param billtoContactPartyId - the bill to contact party id
   * @param billtoPartySiteId - the bill to address id
   * @param billtoPartySiteType - the bill to address id
   * @param taxDetailId - the id of the tax detail record
   * @param taxable - flag to indicate if the order is taxable
   * @param taxExemptReason - the tax exemption reason if the order is tax exempt
   * @param taxExemptNumber - the tax certificate number if the order is not taxable
   * @param poNumber - po number
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void saveCashPayment(String cartId, String paymentId,
                              String lastModifiedTimestamp,
                              String billtoCustomerAccountId,
                              String billtoContactPartyId,
                              String billtoPartySiteId,
                              String billtoPartySiteType, String taxDetailId,
                              boolean taxable, String taxExemptReason,
                              String taxExemptNumber, String poNumber) throws FrameworkException,
                              SQLException, ShoppingCartException,
                              QuoteException
  {
    setupShopCartPaymentInfo(cartId, paymentId, taxDetailId, taxable,
                             taxExemptReason, taxExemptNumber,
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType, poNumber);

    this.paymentType = CASH_PAYMENT;
    this.paymentNumber = null;

    savePaymentInformation(false);
  }


  protected static final ShoppingCart loadAndFill(String cartId_s,
          
                                               String[] cartLineIds_s,
                                               String retrievalNumber_s,
                                               CartLoadControlRecord loadControlRec)
          throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadAndFill";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called: cartId: " + cartId_s);
}
    if (cartLineIds_s != null && cartLineIds_s.length > 0)
    {
      for (int l = 0; l < cartLineIds_s.length; l++)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called: cartLineIds[" + l + "]: " + cartLineIds_s[l]);
}
      }
    }
    //some "smart" checking
    if (loadControlRec.lineBilling) {
      loadControlRec.headerBilling = true;
    }
    if (loadControlRec.lineShipping) {
      loadControlRec.headerShipping = true;
    }

    loadControlRec.logValues(METHOD);

    BigDecimal cartId = null;
    BigDecimal[] cartLineIds = null;

    BigDecimal retrievalNumber = makeRetrievalNumBigDecimal(retrievalNumber_s);
    try {
      if ((cartId_s != null) && !"".equals(cartId_s)) cartId = new BigDecimal(cartId_s);
      if (cartLineIds_s != null && cartLineIds_s.length > 0)
      {
        cartLineIds = new BigDecimal[cartLineIds_s.length];
        for (int cl = 0; cl < cartLineIds_s.length; cl++)
        {
          if ((cartLineIds_s[cl] != null) && !"".equals(cartLineIds_s[cl])) cartLineIds[cl] = new BigDecimal(cartLineIds_s[cl]);
        }
      }
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }

    ShoppingCart shopCart = ShoppingCart.doQuoteLoad(cartId, cartLineIds, retrievalNumber, loadControlRec);

    if((shopCart != null) && (shopCart.headerRec != null))
    {
      shopCart.loadControlRec = loadControlRec;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart seems to be okay");
}
      if(loadControlRec.includeOrdered)
      {

        BigDecimal  orderNumberBig =
          Quote.getOrderNumber(shopCart.headerRec.quote_header_id);

        if(orderNumberBig != null)
        {
          shopCart.orderNumber = orderNumberBig.toString();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "orderNumber is " + shopCart.orderNumber);
}
          // new condition - only set cart total in cookie if cart is the active cart
          // (either the cartid is the one in the cookie, or we passed in a null and retrieved the active cart)
          if(!loadControlRec.headerContract &&
             (shopCart.headerRec.quote_header_id.equals(RequestCtx.getCartId()) || (cartId == null)))
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Resetting total in cookie");
}
            RequestCtx.setCartTotal(PriceObject.formatNumber(shopCart.currencyCode,
                    (double) 0));
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Resetting total in cookie");
}
          }
        }
      }
      // new condition - only set cart total in cookie if cart is the active cart
      // (either the cartid is the one in the cookie, or we passed in a null and retrieved the active cart)
      else if(!loadControlRec.headerContract &&
              (shopCart.headerRec.quote_header_id.equals(RequestCtx.getCartId()) || (cartId == null)))
      {
        // calling this early so that the cookie cart total can be set correctly
        // there's no worry about duplicate processing as the api sets a flag to say it's been run
        shopCart.fillHeaderInformation();
        shopCart.fillSoldtoInformation();

        if (loadControlRec.showPrice) {
          shopCart.fillPriceSummaryInformation();
        }
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Resetting total in cookie");
}
        if (loadControlRec.formatNetPrices) {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting totalPrice");
}
          RequestCtx.setCartTotal(shopCart.totalPrice);
        } else {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting totalListPrice");
}
          RequestCtx.setCartTotal(shopCart.totalListPrice);
        }
        // added 12/11/03: Conc Issue: addToCart
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting lastModifiedTimestamp: "+shopCart.lastModifiedTimestamp); }
        RequestCtx.setCartLastUpdateDate(shopCart.lastModifiedTimestamp);

        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Resetting total in cookie");
}
      }
    }
    else // if we had trouble loading the cart, return null;
    {
      // set price to zero in cookie as long as it wasn't a contract cart we failed on
      // new condition - only set cart total in cookie if cart is the active cart
      // (either the cartid is the one in the cookie, or we passed in a null and retrieved the active cart)
      if(!loadControlRec.headerContract &&
              ((cartId == null) || cartId.equals(RequestCtx.getCartId())))
       {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ERROR in loading cart, Resetting total in cookie");
}
        RequestCtx.setCartTotal(PriceObject.formatNumber(RequestCtx.getCurrencyCode(),
                (double) 0));
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Resetting total in cookie");
}
      }

      // try removing the active cartId
      // (either the cartid is the one in the cookie,
      //   or we passed in a null and retrieved the active cart)
      // and retrievalNumber for Recipient flow, if load Fails.
      if((cartId == null) || cartId.equals(RequestCtx.getCartId()))
      {
         if(logEnabled) { IBEUtil.log(CLASS, METHOD,"load failed, removing cartId from cookie");
}
         RequestCtx.removeCookieValue(RequestCtx.CART_ID);
      }
      if(retrievalNumber !=null && retrievalNumber.equals(RequestCtx.getShareeNumber()))
      {
         if(logEnabled) { IBEUtil.log(CLASS, METHOD,"load failed, removing retrieval number from cookie");
}
         RequestCtx.removeCookieValue(RequestCtx.SHAREE_NUMBER);
      }
      return null;
    }


    shopCart.fillHeaderInformation();
    shopCart.fillSoldtoInformation();

    if (loadControlRec.showPrice) {
      shopCart.fillPriceSummaryInformation();
    }
    if (loadControlRec.headerShipping) {
      shopCart.fillHeaderShippingDetails();
    }
    // this condition & the third parameter used to be based on loadControlRec.headerTax
    // but we took it out & hardcoded it to true becuase it didn't really save much (inside of
    // fillHeaderBillingPaymentTaxDetails api) and in order to use it where it did save more
    // (inside of fillPriceSummaryInformation - to turn off a db hit to TaxInfo.getTaxInfo)
    if (loadControlRec.headerBilling || loadControlRec.headerPayment || loadControlRec.headerTax) {
      shopCart.fillHeaderBillingPaymentTaxDetails(loadControlRec.headerBilling,
                                                  loadControlRec.headerPayment,
                                                  loadControlRec.headerTax);
    }
    if (loadControlRec.headerContract) {
      shopCart.associatedContract = Contract.getContract(shopCart.headerRec.quote_header_id);
    }
    if (loadControlRec.headerAgreement) {
      // load header level agreement (if one is set)
      if (shopCart.headerRec.contract_id != null) {
        shopCart.agreementInfo = Agreement.load(shopCart.headerRec.contract_id);
      } else {
        shopCart.agreementInfo = new Agreement(); 
      }
    }

    if (loadControlRec.showPrice || // even if only to calculate the prices
        loadControlRec.lineBilling ||
        loadControlRec.lineAgreements ||
        loadControlRec.lineCommitments ||
        loadControlRec.lineShipping ||
        loadControlRec.lineTax ||
        loadControlRec.loadItems) {
      // takes care of any and all line level details for all lines
      fillAllLinesAllDetails(shopCart);
    }
    if (loadControlRec.isShared) shopCart.isShared = ShoppingCartUtil.isCartShared(cartId_s);
    return shopCart;
  }

  private static final ShoppingCart doQuoteLoad(BigDecimal cartId,
                                               BigDecimal[] cartLineIds,
                                               BigDecimal retrievalNumber,
                                               CartLoadControlRecord loadControlRec)
          throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "doQuoteLoad";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called: cartId: " + cartId);

}
    BigDecimal partyID = RequestCtx.getPartyId();
    BigDecimal custAcctID = RequestCtx.getAccountId();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "  partyID   : " + partyID);
    IBEUtil.log(CLASS, METHOD, "  custAcctID: " + custAcctID);
}
    if((cartId == null) || (cartId.equals(EMPTY_STRING)) || (cartId.equals(gMissNumStr)))
    {
      if(RequestCtx.userIsLoggedIn())
      {
        cartId     = null;
//        partyID    = RequestCtx.getPartyId();
//        custAcctID = RequestCtx.getAccountId();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "no cartId, user logged in - setting to find active cart for partyID: " + partyID + " custAcctID: " + custAcctID);
}
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "cartId is unreadable and user is not logged in, returning null");
}
        return null;
      }
    }
    QuoteLoadControlRecord quoteLoadControlRec = new QuoteLoadControlRecord();

    quoteLoadControlRec.loadHeaderPayment    = loadControlRec.headerPayment;
    // loadLinePayment is hardcoded to false since nothing supports this feature on the backend
    quoteLoadControlRec.loadLinePayment      = false;
    quoteLoadControlRec.loadHeaderShipment   = loadControlRec.headerShipping;
    quoteLoadControlRec.loadLineShipment     = loadControlRec.lineShipping;
    quoteLoadControlRec.loadHeaderTaxDetail  = loadControlRec.headerTax;
    // loadLineTax if false here since it gets handled by a separate api
    quoteLoadControlRec.loadLineTaxDetail    = false;
    quoteLoadControlRec.includeOrdered       = loadControlRec.includeOrdered;
    quoteLoadControlRec.loadType             = loadControlRec.loadType;
    quoteLoadControlRec.validateUser         = loadControlRec.validateUser;
    quoteLoadControlRec.headerLoadDepth      = loadControlRec.headerLoadDepth;
    quoteLoadControlRec.accessLevel          = loadControlRec.accessLevel;
    quoteLoadControlRec.shareType            = loadControlRec.shareType;
    quoteLoadControlRec.checkReprice         = loadControlRec.checkReprice;

    if (loadControlRec.checkReprice) {
      quoteLoadControlRec.sessionCurrencyCode = RequestCtx.getCurrencyCode();
      // we should only do this check if the profile for msite price listing is turned on
      // the StoreMinisite api internally does this check for us. thanks, StoreMinisite!
      quoteLoadControlRec.sessionPriceListID = StoreMinisite.getPriceListID();
    }

    quoteLoadControlRec.loadLine       = false;
    quoteLoadControlRec.loadLineDetail = false;
    quoteLoadControlRec.loadLineRel    = false;

    if (loadControlRec.lineBilling || loadControlRec.loadItems) {
      quoteLoadControlRec.loadLine = true;
    }
    if (loadControlRec.loadItems) {
      quoteLoadControlRec.loadLineDetail = true;
      quoteLoadControlRec.loadLineRel    = true;
    }
    if (cartLineIds == null && !loadControlRec.loadItems) {
      quoteLoadControlRec.loadLineRel    = false;
    }
    quoteLoadControlRec.loadLineAttrExt     = false;
    quoteLoadControlRec.loadHeaderPriceAttr = false;
    quoteLoadControlRec.loadLinePriceAttr   = false;

    ShoppingCart shCart = null;
//    try {
      shCart = new ShoppingCart( cartId ,
                                 cartLineIds        ,
                                 partyID            ,
                                 custAcctID         ,
                                 retrievalNumber    ,
                                 quoteLoadControlRec);
//    } catch (QuoteException e)
//    {
//      IBEUtil.log(CLASS, METHOD, "QuoteException error shCart: " + shCart);
      if (shCart != null) {
       if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shCart not null");
                    }
       if (shCart.headerRec != null) {
         if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shCart.headerRec.quote_header_id: " + shCart.headerRec.quote_header_id);
                }
       }
      }
//      IBEUtil.log(CLASS, METHOD, "QuoteException error: " + e.getMessage());
      if ((loadControlRec.checkReprice)
          && shCart.checkReprice(quoteLoadControlRec))
      { // do repricing if we were asked to, and we have a valid cart, and either the currency code or price list don't match

      //&& (e.getMessage() == "CURRENCY_CODE_MISMATCH")) {
        //reprice & reload
//        String cartId_s = null;
//        if (cartId != null) cartId_s = cartId.toString();
//        ShoppingCart.repriceCart(cartId_s,quoteLoadControlRec.sessionCurrencyCode);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "checkReprice was turned on, the api indicates we need to reprice, so calling repriceCart");
        }
        ShoppingCart.repriceCart(shCart.headerRec.quote_header_id.toString(),quoteLoadControlRec.sessionCurrencyCode, shCart.headerRec.last_update_date);
        // turn off this check for the 2nd load as the reprice may not have made everything match (bug 3391118)
        quoteLoadControlRec.checkReprice = false;
        shCart = new ShoppingCart(shCart.headerRec.quote_header_id ,
                                 cartLineIds        ,
                                 partyID            ,
                                 custAcctID         ,
                                 retrievalNumber    ,
                                 quoteLoadControlRec);
      }// else {
      //  throw e;
     // }
//    }


    return shCart;
  }

  /**
   * LOAD #1: Loads a cart with its items. If the cartId is null or empty string, the
   * API attempts to load the active cart if the user is logged in, else it
   * returns null.<BR>
   * <BR>
   * This signature is primarily for backward compatability.  Calls LOAD #2 with the input cartId and loadLineTaxDetails set to false.
   *
   * @param cartId the cart id.
   * @return ShoppingCart
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
   // #1
  public static final ShoppingCart load(String cartId)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    return load(cartId, false);
  }

 /**
   * LOAD #2: Loads a cart with its items. If the cartId is null or empty string, the
   * API attempts to load the active cart if the user is logged in, else it
   * returns null.<BR>
   * <BR>
   * This signature is primarily for backward compatability.  Calls LOAD #3 with the input cartId, loadLineTaxDetails, and loadCommitmentInfo set to false.
   *
   * @param cartId the cart id
   * @param loadLineTaxDetails flag to load line tax details or not
   * @return ShoppingCart object
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException the error message will indicate the nature of
   * of the error
   */
   // #2
  public static final ShoppingCart load(String cartId, boolean loadLineTaxDetails)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    return load(cartId, loadLineTaxDetails, false);
  }

  /**
   * LOAD #3: Loads a cart with its items. If the cartId is null or empty string, the
   * API attempts to load the active cart if the user is logged in, else it
   * returns null.<BR>
   * <BR>
   * This signature is primarily for backward compatability.  Calls loadAndFill with null for cartLineIds, the cookie retrieval number, and the loadControlRecord set as such:<BR>
   * loadControlRec.fillChildItems  = true<BR>
   * loadControlRec.loadItems       = true<BR>
   * loadControlRec.showPrice       = true<BR>
   * loadControlRec.defaultPreferences = true<BR>
   * loadControlRec.lineCommitments = loadCommitmentInfo<BR>
   * loadControlRec.lineTax         = loadLineTaxDetails<BR>
   * loadControlRec.loadType        = ShoppingCart.LOAD_CART<BR>
   *
   * @param cartId the cart id.
   * @param loadLineTaxDetails flag whether to load tax details or not.
   * @param loadCommitmentInfo flag whether to load commitment info or not.
   * @return ShoppingCart
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException the error message will indicate the nature of
   * of the error
   */
   // #3
  public static final ShoppingCart load(String cartId, boolean loadLineTaxDetails, boolean loadCommitmentInfo)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {

    String METHOD = "load";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
    IBEUtil.log(CLASS, METHOD, "loadLineTaxDetails = " + loadLineTaxDetails);
    IBEUtil.log(CLASS, METHOD, "loadCommitmentInfo = " + loadCommitmentInfo);
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.fillChildItems  = true;
    loadControlRec.loadItems       = true;
    loadControlRec.showPrice       = true;
    loadControlRec.defaultPreferences = true;
    loadControlRec.lineCommitments = loadCommitmentInfo;
    loadControlRec.lineTax         = loadLineTaxDetails;
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;
    shopCart = loadAndFill(cartId, null, getRetrievalNumberString(),loadControlRec);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;
  }

  /**
   * Loads a cart with its items. If the unique identifier of the cart is null or empty
   * string, then the method attempts to load the active cart if the user is logged in, else it
   * returns null.
   *
   * Behavior for loadControlRec.headerShipping = true :
   * <ul>
   * <li> If the cartId is null or invalid, the method tries to load the active
   * cart for the user, if the user is logged in, else it returns null.
   * <li> Contact information is loaded only for a sales representative or a B2B user.
   * <li> The Ship To customer will be the same as the Sold To customer
   * if there is no Ship To customer account id in the database for the cart.
   * <li> If a B2B user is logged in and is shipping to himself,
   * i.e if no Ship To account id is specified and there is no Ship To address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the Ship To address as
   * the primary shipping address, if any, of the customer.
   * <li> If the Ship To customer is different from the Sold To customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * Ship To customer.
   * <li> If the cart has a Ship To address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If item shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * </ul>
   * Behavior for loadControlRec.headerBilling = true :
   * <ul>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The Bill To customer will be the same as the Sold To customer
   * if there is no Bill To customer account id in the database for the cart.
   * <li> If a B2B user is logged in and is billing to himself,
   * i.e if no Bill To account id is specified and there is no Bill To address
   * and no contact party in the cart,the method sets the contact as the customer
   * itself and the Bill To address as the primary billing address, if any, of the customer.
   * <li> If the Bill To customer is different from the Sold To customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * Bill To customer.
   * <li> If the cart has a Bill To address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * if the loadControlRec.lineBilling parameter is true, the api will turn on headerBilling; likewise
   * for the lineShipping parameter, the api will turn on headerShipping.<BR>
   *
   * @param cartId The unique identifier of the cart .
   * @param cartLineIds The unique identifiers of lines if specific lines are being requested; null otherwise.
   * @param retrievalNumber The retrieval number of a member of a shared cart loading the cart; null otherwise.
   * @param loadControlRec The {@link oracle.apps.ibe.shoppingcart.quote.ControlRecord ControlRecord} object specifying what to load.
   *
   * @return The {@link oracle.apps.ibe.shoppingcart.quote.ShoppingCart ShoppingCart} object.
   *
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message indicates the nature of the error
   * @throws ShoppingCartException the error message indicates the nature of the error
   * @rep:displayname Load Cart
   */
   // #6
  public static final ShoppingCart load(String cartId, String[] cartLineIds, String retrievalNumber,
                                        CartLoadControlRecord loadControlRec)
          throws FrameworkException, SQLException,
                 QuoteException, ShoppingCartException
  {
    if (cartLineIds != null && cartLineIds.length > 0)
      return ShoppingCart.loadAndFill(cartId,cartLineIds,retrievalNumber,loadControlRec);
    else
      return ShoppingCart.loadAndFill(cartId,null,retrievalNumber,loadControlRec);
  }

  /**
   * Loads a cart with its items. Same as load. If the cartId is null or empty s tring,
   * the API attempts to load the active cart if the user is logged in, else it
   * returns null.
   * @param cartId - the cart id.
   *
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
  * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.fillChildItems  = true;<BR>
    loadControlRec.loadItems       = true;<BR>
    loadControlRec.showPrice       = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineTax         = false;
   */
  public static final ShoppingCart loadWithItems(String cartId)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    return load(cartId, false);
  }

  /**
   * Loads a cart with its items. Same as load. If the cartId is null or empty string,
   * the API attempts to load the active cart if the user is logged in, else it
   * returns null.
   * @param cartId - the cart id.
   *
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
  * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.fillChildItems  = true;<BR>
    loadControlRec.loadItems       = true;<BR>
    loadControlRec.showPrice       = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineTax         = loadLineTaxDetails;
   */
  public static final ShoppingCart loadWithItems(String cartId,
          boolean loadLineTaxDetails) throws FrameworkException,
                                             SQLException, QuoteException,
                                             ShoppingCartException
  {
    return load(cartId, loadLineTaxDetails);
  }

  /**
   * Loads a cart with its items and the commitment information.
   * Same as load. If the cartId is null or empty string,
   * the API attempts to load the active cart if the user is logged in, else it
   * returns null.
   * @param cartId - the cart id.
   *
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
   * loadControlRec.fillChildItems  = false;<BR>
   * loadControlRec.loadItems       = true;<BR>
   * loadControlRec.showPrice       = false;<BR>
   * loadControlRec.lineCommitments = true;<BR>
   * loadControlRec.lineAgreements  = true;<BR>
   * loadControlRec.defaultPreferences = true;<BR>
   * loadControlRec.loadType        = ShoppingCart.LOAD_CART;<BR>
   *
   */
  public static final ShoppingCart loadItemsWithCommitmentInfo(String cartId) throws FrameworkException,
                                             SQLException, QuoteException,
                                             ShoppingCartException
  {
    String METHOD = "loadItemsWithCommitmentInfo";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.fillChildItems  = false;
    loadControlRec.loadItems       = true;
    loadControlRec.showPrice       = false;
    loadControlRec.lineCommitments = true;
    loadControlRec.lineAgreements  = true;
    loadControlRec.defaultPreferences = true;    
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;

    shopCart = loadAndFill(cartId, null,getRetrievalNumberString(), loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;

  }

  /**
   * Loads a cart with its items and the commitment information.
   * Same as load. If the cartId is null or empty string,
   * the API attempts to load the active cart if the user is logged in, else it
   * returns null.
   * @param cartId - the cart id.
   *
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.headerAgreement = true;<BR>
    loadControlRec.loadItems       = lineAgreements;<BR>
    loadControlRec.lineAgreements  = lineAgreements;<BR>
    loadControlRec.fillChildItems  = false;<BR>
    loadControlRec.showPrice       = false;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;
   */
  public static final ShoppingCart loadWithAgreementInfo(String cartId, boolean lineAgreements) throws FrameworkException,
                                             SQLException, QuoteException,
                                             ShoppingCartException
  {
    String METHOD = "loadWithAgreementInfo";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.headerAgreement = true;
    loadControlRec.loadItems       = lineAgreements;
    loadControlRec.lineAgreements  = lineAgreements;
    loadControlRec.fillChildItems  = false;
    loadControlRec.showPrice       = false;
    loadControlRec.defaultPreferences = true;
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;

    shopCart = loadAndFill(cartId, null,getRetrievalNumberString(), loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;

  }

  /**
   * Loads a cart with the shipping information. A boolean parameter indicates
   * if line level shipping information is to be loaded.
   * <ul> <li> If the cartId is null or invalid, the method tries to load the active
   * cart for the user, if the user is logged in, else it returns null.
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The ship to customer will be the same as the sold to customer
   * if there is no ship to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is shipping to himself,
   * i.e if no ship to account id is specified and there is no ship to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the ship to address as
   * the primary shipping address, if any, of the customer.
   * <li> If the ship to customer is different from the sold to customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * ship to customer.
   * <li> If the shopCart has a ship to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If line shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * </ul>
   * @param cartId - the cart id
   * @param loadLineShipment - indicates if line level shipping information is
   * to be loaded.
   * @return ShoppingCart - can be null if the cart is ordered or cannot be loaded
   * because of an error.
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.headerShipping = true;<BR>
    if (loadLineShipment) {<BR>
      loadControlRec.lineShipping = true;<BR>
      loadControlRec.loadItems    = true;<BR>
    }<BR>
    loadControlRec.defaultPreferences = true;
   */
  public static final ShoppingCart loadWithShipment(String cartId,
          boolean loadLineShipment) throws FrameworkException, SQLException,
                                           QuoteException,
                                           ShoppingCartException
  {
    String METHOD = "loadWithShipment";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.headerShipping = true;
    if (loadLineShipment) {
      loadControlRec.lineShipping = true;
      loadControlRec.loadItems    = true;
    }
    loadControlRec.defaultPreferences = true;
    shopCart = loadAndFill(cartId, null,getRetrievalNumberString(), loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCart = " + shopCart);
    IBEUtil.log(CLASS, METHOD, "DONE");
}
    return shopCart;
  }

  /**
   * Loads cart level (header level) shipping information.
   *
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   */
  private void fillHeaderShippingDetails()
          throws SQLException, FrameworkException, QuoteException
  {
    String METHOD = "fillHeaderShippingDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    boolean     salesRep = false;
    boolean     bizCust = false;
    boolean     isSharee = (RequestCtx.getShareeNumber() == null) ? false
                           : true;

    BigDecimal  partyId = RequestCtx.getPartyId();
    BigDecimal  acctId = RequestCtx.getAccountId();

    if(RequestCtx.userIsSalesRep())
    {
      salesRep = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "is sales rep");
}
    }

    if(RequestCtx.userIsBusinessCustomer())
    {
      bizCust = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "is biz user");
}
    }

    //loadHeaderInformation();
    //loadSoldtoInformation();

    // loadPriceSummaryInformation();

    oracle.apps.ibe.shoppingcart.quote.ShipmentRecord headerShipRecord = null;

    if(headerShipmentRec != null && headerShipmentRec.length > 0
            && headerShipmentRec[0] != null)
    {
      headerShipRecord = headerShipmentRec[0];
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Header ship record exists");
}
    }
    else
    {
      headerShipRecord =
        new oracle.apps.ibe.shoppingcart.quote.ShipmentRecord();
    }

    if(headerShipRecord.shipping_instructions != gMissChar)
    {
      shippingInstructions =
        IBEUtil.nonNull(headerShipRecord.shipping_instructions);
    }

    if(headerShipRecord.packing_instructions != gMissChar)
    {
      packingInstructions =
        IBEUtil.nonNull(headerShipRecord.packing_instructions);
    }

    if((headerShipRecord.request_date != null)
            && (!headerShipRecord.request_date.equals(gMissDate)))
    {
      requestedDeliveryDate =
        ShoppingCartUtil.formatTimestamp(headerShipRecord.request_date);
    }

    if((headerShipRecord.shipment_id != null)
            && (headerShipRecord.shipment_id != gMissNum))
    {
      shipmentId = headerShipRecord.shipment_id.toString();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, " shipmentId is = " + shipmentId);
}
    }

    if(headerShipRecord.ship_method_code != FndConstant.getGMissChar())
    {
      shippingMethod = IBEUtil.nonNull(headerShipRecord.ship_method_code);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, " shippingMethod is = " + shippingMethod);
         }
      // load shippingMethodDescription
      LookupObject[]  los = QuoteUtil.getAllShippingMethods();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, " Got all shippping methods");
         }
      if(los != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " Shipping methods exist");
          }
        int losLength = los.length;

        for(int k = 0; k < losLength; k++)
        {
          if(los[k].getLookupCode().equals(shippingMethod))
          {
            shippingMethodDescription = los[k].getMeaning();
          }
        }
      }
    }

    if((headerShipRecord.ship_to_party_site_id != null)
            && (headerShipRecord.ship_to_party_site_id != gMissNum))
    {
      shiptoPartySiteId = headerShipRecord.ship_to_party_site_id.toString();

    }

    if((headerShipRecord.ship_to_cust_account_id != null)
            && (headerShipRecord.ship_to_cust_account_id != gMissNum))
    {
      shiptoCustomerAccountId =
        headerShipRecord.ship_to_cust_account_id.toString();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
        " shiptoCustomerAccountId from DB is = " + shiptoCustomerAccountId);
          }
    }

    /*
     * if the customer is logged in  (b2b user), and is shipping to himself,
     * i.e if the ship to account id is the same as the account in the header and
     * there is no ship to address and no contact party in the shopCart,
     * set the contact as the customer itself and the ship to address as
     * the primary shipping address, if any, of the customer
     *
     * 7/11/02 - bug 2456726 - default only if "first time" - ie - no shipmentId
     */

    if((bizCust &&!salesRep) && (shipmentId.equals(EMPTY_STRING))
            && (shiptoCustomerAccountId.equals(EMPTY_STRING))
            && ((headerShipRecord.ship_to_party_site_id == null)
                || (headerShipRecord.ship_to_party_site_id == gMissNum))
            && ((headerShipRecord.ship_to_party_id == null)
                || (headerShipRecord.ship_to_party_id == gMissNum)))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
        "B2B user and no shipment id, ship accnt, and addrs exist, defaulting..");
         }
      if (loadControlRec.defaultPreferences) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling AddressManager.getPrimaryAddressId");
          }
        /* primary shipping address, if any, of user party_id */
        BigDecimal primaryAddrId = AddressManager.getPrimaryAddressId(partyId,
                Address.SHIP_TO, RequestCtx.getOperatingUnit());
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling AddressManager.getPrimaryAddressId");
          }
        if(primaryAddrId != null)
        {
          shiptoPartySiteId = primaryAddrId.toString();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoPartySiteId = " + shiptoPartySiteId);
            }
        }
      }

      /*
       * as per PM, default the contact only the first time,i.e. when shipmentId
       * is not available in the cart
       */
      if(!isSharee)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "Not a sharee, defualting contact to party in quote");
           }
        shiptoContactPartyId = headerRec.party_id.toString();
        shiptoContactName = IBEUtil.nonNull(headerRec.person_first_name)
                            + " "
                            + IBEUtil.nonNull(headerRec.person_last_name);
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "Is a sharee, defualting contact to party in cookie");
          }
        shiptoContactPartyId = partyId.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to call RequestCtx.getPersonName");
         }
        shiptoContactName = RequestCtx.getPersonName();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done call RequestCtx.getPersonName");
            }
        if ( (shiptoContactName == null)
          || (shiptoContactName.equals(EMPTY_STRING)) )
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "NAme in cookie is not present");
         }
          shiptoContactName =  ContactManager.get(partyId).getPerson().getPartyName();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done with call to ContactManager.get(partyId).getPerson().getPartyName()");
          }
        }
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoContactPartyId = " + shiptoContactPartyId);
      IBEUtil.log(CLASS, METHOD, "shiptoContactName = " + shiptoContactName);
         }
    }

    boolean shacntnull = false;

    if(shiptoCustomerAccountId.equals(EMPTY_STRING))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoCustomerAccountId is blank,  defaulting...");
         }
      // in case of no account id, if a shipment record is present, the account must be the soldto
      // if there is no shipment record, do the normal logic (if not sharee, use soldto; if sharee, use cookie)
      if(!isSharee || !shipmentId.equals(EMPTY_STRING))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "either not a sharee or we have shipment record, defaulting to soldto");
             }
        shiptoCustomerName = IBEUtil.nonNull(headerRec.party_name);

        if(headerRec.cust_account_id != null)
        {
          shiptoCustomerAccountId = headerRec.cust_account_id.toString();
        }
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "IS a sharee, defaulting accnt to cookie acnt");
          }
        shiptoCustomerAccountId = acctId.toString();
      }
    }
    else
    {
      shiptoCustomerName =
        IBEUtil.nonNull(headerShipRecord.ship_to_cust_name);
    }

    /* Get the party_id and type of party owning shiptoCustomerAccountId */
    String[]  idNameType =
      ShoppingCartUtil.getIdNameTypeofAccount(shiptoCustomerAccountId);

    shiptoCustomerPartyType = idNameType[2].toLowerCase();
    shiptoCustomerPartyId = idNameType[0];

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoCustomerPartyType = " + shiptoCustomerPartyType);
    IBEUtil.log(CLASS, METHOD, "shiptoCustomerPartyId = " + shiptoCustomerPartyId);
}
    if(isSharee)
    {
      shiptoCustomerName = idNameType[1];
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoCustomerName = " + shiptoCustomerName);

}
    if((shiptoCustomerAccountId != null)
            && (!shiptoCustomerAccountId.equals(EMPTY_STRING))
            && (!shiptoCustomerAccountId.equals(customerAccountId))
            && ((headerShipRecord.ship_to_party_site_id == null)
                || (headerShipRecord.ship_to_party_site_id == gMissNum))
            && loadControlRec.defaultPreferences)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Accnt is okay now, but no ship to addrs in DB");
       }
      BigDecimal primaryAddrId =
        AddressManager.getPrimaryAddressId(new BigDecimal(shiptoCustomerPartyId),
                                      Address.SHIP_TO, RequestCtx.getOperatingUnit());
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "After call to AddressManager.getPrimaryAddressId");
          }
      if(primaryAddrId != null)
      {
        shiptoPartySiteId = primaryAddrId.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoPartySiteId = " + shiptoPartySiteId);
         }
      }
    }

    /*
     * If the ship to party id is not the same as the party id of the ship to (customer)
     * it means that we are shipping to a contact. Then set the contact party id to be the
     * party id in the ship to record
     */

    if((headerShipRecord.ship_to_party_id != null)
      && (headerShipRecord.ship_to_party_id != gMissNum)
      && (!headerShipRecord.ship_to_party_id.toString().equals(shiptoCustomerPartyId)))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Okay, we have a ship to contact in the DB");
}
      shiptoContactPartyId = headerShipRecord.ship_to_party_id.toString();
      shiptoContactName =
        IBEUtil.nonNull(headerShipRecord.ship_to_contact_first_name) + " "
        + IBEUtil.nonNull(headerShipRecord.ship_to_contact_last_name);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoContactPartyId = " + shiptoContactPartyId);
       IBEUtil.log(CLASS, METHOD, "shiptoContactName = " + shiptoContactName);
         }
    }

    if((shiptoPartySiteId != null) && (!shiptoPartySiteId.equals("")))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Determine type of address");
          }
      /*
       * if the shopCart has a ship to address, then we need to determine the
       * onwer of the address. If the owner is the same as the customer,
       * then set the party site type to customer, else set it to contact
       */

      String partyIdOfSite = ShoppingCartUtil.getPartyIdOfPartySite(shiptoPartySiteId);
      if(shiptoCustomerPartyId.equals(partyIdOfSite))
      {
        shiptoPartySiteType = "customer";
      }
      else
      {
        shiptoPartySiteType = "contact";
      }
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoPartySiteType = " + shiptoPartySiteType);
                             }
    if((bizCust || salesRep) && (!shiptoContactPartyId.equals("")))
    {
      String[]  emailAndPhone = getPrimaryEmailAndPhone(shiptoContactPartyId);

      shiptoContactEmail = IBEUtil.nonNull(emailAndPhone[3]);
      shiptoContactPhone = IBEUtil.nonNull(emailAndPhone[0]);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shiptoContactEmail = " + shiptoContactEmail);
      IBEUtil.log(CLASS, METHOD, "shiptoContactPhone = " + shiptoContactPhone);
}
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Loads a cart along with payment related information. A flag specifies if the
   * payment information at the cart item (line) level is to be loaded.
   * <ul>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The bill to customer will be the same as the sold to customer
   * if there is no bill to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is billing to himself,
   * i.e if no bill to account id is specified and there is no bill to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the bill to address as
   * the primary billing address, if any, of the customer.
   * <li> If the bill to customer is different from the sold to customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * bill to customer.
   * <li> If the shopCart has a bill to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * @param cartId - the cart id.
   * @param loadLinePayment - flag to indicate if line level payment information
   * is to be loaded. (<B> This feature is to be implemented </B>).
   * @return ShoppingCart
   * 
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.headerBilling = true;<BR>
    loadControlRec.headerPayment = true;<BR>
    loadControlRec.headerTax     = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    if (loadLinePayment) {<BR>
      loadControlRec.lineBilling = true;<BR>
      loadControlRec.lineTax     = true;<BR>
      loadControlRec.loadItems   = true;<BR>
    }
   */
  public static final ShoppingCart loadWithPayment(String cartId,
          boolean loadLinePayment) throws FrameworkException, SQLException,
                                          QuoteException,
                                          ShoppingCartException
  {
    String METHOD = "loadWithPayment";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cart Id = " + cartId);
    IBEUtil.log(CLASS, METHOD, "loadLinePayment = " + loadLinePayment);

}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.headerBilling = true;
    loadControlRec.headerPayment = true;
    loadControlRec.headerTax     = true;
    loadControlRec.defaultPreferences = true;
    if (loadLinePayment) {
      loadControlRec.lineBilling = true;
      loadControlRec.lineTax     = true;
      loadControlRec.loadItems   = true;
    }
    shopCart = loadAndFill(cartId, null,getRetrievalNumberString(), loadControlRec);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);
}
    return shopCart;
  }

  /**
   * Loads payment information for the cart.
   * @param headerBillingDefaultIdsOnly - if set to true, this api will set
   *        fillPayment & fillTax to false, and for billing, only default and set the id's
   *
   * @param loadLinePayments - flag to indicate if payment informationat the
   * line level is to be loaded. (<B> This feature is to be implemented </B>).
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   */
  private void fillHeaderBillingPaymentTaxDetails(boolean fillBilling, boolean fillPayment, boolean fillTax)
          throws FrameworkException, SQLException, QuoteException
  {
    String METHOD = "fillHeaderBillingPaymentTaxDetails";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "  fillBilling                 " + fillBilling);
    IBEUtil.log(CLASS, METHOD, "  fillPayment                 " + fillPayment);
    IBEUtil.log(CLASS, METHOD, "  fillTax                     " + fillTax);
}
    boolean     salesRep = false;
    boolean     bizCust = false;
    boolean     isSharee = (RequestCtx.getShareeNumber() == null) ? false
                           : true;
    BigDecimal  partyId = RequestCtx.getPartyId();
    BigDecimal  acctId = RequestCtx.getAccountId();

    if(RequestCtx.userIsSalesRep())
    {
      salesRep = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sales rep");
}
    }

    if(RequestCtx.userIsBusinessCustomer())
    {
      bizCust = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a biz customer");
}
    }

//    loadHeaderInformation();
//    loadSoldtoInformation();

    // loadPriceSummaryInformation();

    // this code is outside of the fillPayment condition because
    // it's needed for billing info defaulting
    oracle.apps.ibe.shoppingcart.quote.PaymentRecord  headerPaymentRecord = null;

    if(headerPaymentRec != null && headerPaymentRec.length > 0
            && headerPaymentRec[0] != null)
    {
      headerPaymentRecord = headerPaymentRec[0];
    }

//--------------------------------fillPayment---------------------------------//
    if (fillPayment) {
    
      if(headerPaymentRecord != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Header payment record exists");
         }
        if(headerPaymentRecord.payment_id != null)
        {
          paymentId = headerPaymentRecord.payment_id.toString();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "payment id " + paymentId);
           }
        }

        if(headerPaymentRecord.payment_type_code != null)
        {
          paymentType = headerPaymentRecord.payment_type_code;
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "payment type is " + paymentType);
          }
        }

        if(headerPaymentRecord.cust_po_number != null)
        {
          poNumber = headerPaymentRecord.cust_po_number;
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "poNumber is " + poNumber);
           }
        }

        if(paymentType.equals(CC_PAYMENT))
        {
          if(headerPaymentRecord.credit_card_expiration_date != null)
          {
            ccExpiryTimestamp =
              headerPaymentRecord.credit_card_expiration_date.toString();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ccExpiryTimestamp is <not logged>");
}
          }

          if(headerPaymentRecord.credit_card_holder_name != null)
          {
            ccHolderName = headerPaymentRecord.credit_card_holder_name;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ccHolderName is " + ccHolderName);
}
          }

          if(headerPaymentRecord.credit_card_code != null)
          {
            ccTypeCode = headerPaymentRecord.credit_card_code;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ccTypeCode is " + ccTypeCode);
}
          }

          if(headerPaymentRecord.cc_hash_code1 != null)
          {
            ccHashCode1 = headerPaymentRecord.cc_hash_code1;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ccHashCode1 is " + ccHashCode1);
}
          }

          if(headerPaymentRecord.cc_hash_code2 != null)
          {
            ccHashCode2= headerPaymentRecord.cc_hash_code2;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "ccHashCode2 is " + ccHashCode2);
}
          }

        }

        if((headerPaymentRecord.payment_ref_number != null)
                && (!headerPaymentRecord.payment_ref_number.equals(EMPTY_STRING)))
        {

           if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Assign PaymentRefnum: paymentType is " + paymentType);
                           }
          
           if(paymentType.equals(CC_PAYMENT))
           {
			 paymentNumber = headerPaymentRecord.mask_cc_num;
             if(logEnabled) { IBEUtil.log(CLASS, METHOD, "paymentNumber is not logged for Credit Card>");
                            } //ibeutil
           } //if
           else
           { //else
		     paymentNumber = headerPaymentRecord.payment_ref_number;
             if(logEnabled) { IBEUtil.log(CLASS, METHOD, "paymentNumber is " + paymentNumber);
                            }
           } //innerelse
        }
        else
        {
          if(headerPaymentRecord.payment_type_code == null)
          {
            paymentType = INVOICE_PAYMENT;
          }
          else if(headerPaymentRecord.payment_type_code.equals(CC_PAYMENT))
          {
            paymentType = FAX_CC_PAYMENT;
          }
          else if(headerPaymentRecord.payment_type_code.equals(PO_PAYMENT))
          {
            paymentType = FAX_PO_PAYMENT;
          }
          else if(headerPaymentRecord.payment_type_code.equals(CASH_PAYMENT))
          {
            paymentType = CASH_PAYMENT;
          }
        }
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "paymentType is " + paymentType);
}
    } // end if fillPayment

    oracle.apps.ibe.shoppingcart.quote.TaxDetailRecord  headerTaxRecord = null;

    if(headerTaxDetailRec != null && headerTaxDetailRec.length > 0
            && headerTaxDetailRec[0] != null)
    {
      headerTaxRecord = headerTaxDetailRec[0];
    }

//--------------------------------fillBilling---------------------------------//
    if (fillBilling) {
      if(headerRec.invoice_to_party_site_id != null)
      {
        billtoPartySiteId = headerRec.invoice_to_party_site_id.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "billtoPartySiteId is " + billtoPartySiteId);
}
      }

      if(headerRec.invoice_to_cust_account_id != null)
      {
        billtoCustomerAccountId =
          headerRec.invoice_to_cust_account_id.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "billtoCustomerAccountId is " + billtoCustomerAccountId);
}
      }

      /*
       * if the customer is logged in  (b2b user), and is billing to himself,
       * i.e if the bill to account id is the same as the account in the header and
       * there is no bill to address and no contact party in the shopCart,
       * set the contact as the customer itself and the bill to address as
       * the primary billing address, if any, of the customer
       */

      /*
       * as per PM, default the contact and address only the first time,i.e. when tax
       * detail rcrd (hdr) is not avl
       */

      if( (bizCust &&!salesRep) && ((headerTaxRecord == null) || (headerTaxRecord.tax_detail_id == null))
              && (billtoCustomerAccountId.equals(EMPTY_STRING))
              && (   (headerRec.invoice_to_party_site_id == gMissNum)
                  || (headerRec.invoice_to_party_site_id == null))
              && ((headerRec.invoice_to_party_id == gMissNum)
                  || (headerRec.invoice_to_party_id == null)))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a biz cust and no bill to accnt and site exist");
}
        if(loadControlRec.defaultPreferences) {
          /* primary billing address, if any, of user party_id */
          BigDecimal primaryAddrId = AddressManager.getPrimaryAddressId(partyId,
                  Address.BILL_TO, RequestCtx.getOperatingUnit());
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "Done calling AddressManager.getPrimaryAddressId");
}
          if(primaryAddrId != null)
          {
            billtoPartySiteId = primaryAddrId.toString();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoPartySiteId is " + billtoPartySiteId);
}
          }
        } // end if defaultPreferences
        
        //  if(paymentId.equals(EMPTY_STRING))
        if (true)
        {
          if(!isSharee)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "Not a sharee, defaulting....");
}
            billtoContactPartyId = headerRec.party_id.toString();
            billtoContactName = IBEUtil.nonNull(headerRec.person_first_name)
                                + " "
                                + IBEUtil.nonNull(headerRec.person_last_name);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoContactPartyId is " + billtoContactPartyId);
            IBEUtil.log(CLASS, METHOD,  "billtoContactName is " + billtoContactName);
}
          }
          else
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "A sharee, defaulting ...!!");
}
            billtoContactPartyId = partyId.toString();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "Calling RequestCtx.getPersonName");
}
            billtoContactName = RequestCtx.getPersonName();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoContactName is " + billtoContactName);
}
            if ( (billtoContactName == null)
              || (billtoContactName.equals(EMPTY_STRING)) )
              billtoContactName = ContactManager.get(partyId).getPerson().getPartyName();
            
if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoContactPartyId is " + billtoContactPartyId);
            IBEUtil.log(CLASS, METHOD,  "billtoContactName is " + billtoContactName);
                }
          }
        }
      }

      boolean blacntnull = false;

      if(billtoCustomerAccountId.equals(EMPTY_STRING))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "bill to accnt is blank");
}
        // in case of no account id, if a tax record is present, the account must be the soldto
        // if there is no tax record, do the normal logic (if not sharee, use soldto; if sharee, use cookie)
        if(!isSharee || ((headerTaxRecord != null) && (headerTaxRecord.tax_detail_id != null)))
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "Since either not a sharee or since we have a tax record, defaulting to soldto...!!");
}
          billtoCustomerName = IBEUtil.nonNull(headerRec.party_name);

          if(headerRec.cust_account_id != null)
          {
            billtoCustomerAccountId = headerRec.cust_account_id.toString();
          }
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "billtoCustomerName is " + billtoCustomerName);
          IBEUtil.log(CLASS, METHOD,
            "billtoCustomerAccountId is " + billtoCustomerAccountId);
             }
        }
        else
        {
          billtoCustomerAccountId = acctId.toString();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "Defaulted from cookie, billtoCustomerAccountId is " + billtoCustomerAccountId);
             }
        }
      }
      else
      {
        billtoCustomerName = IBEUtil.nonNull(headerRec.invoice_to_cust_name);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "From DB, billtoCustomerName is " + billtoCustomerName);
}
      }

      /* Get the party_id and type of party owning billtoCustAccountId */
      String[]  idNameType =
      ShoppingCartUtil.getIdNameTypeofAccount(billtoCustomerAccountId);

      billtoCustomerPartyType = idNameType[2].toLowerCase();
      billtoCustomerPartyId = idNameType[0];

      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
        "billtoCustomerPartyType is " + billtoCustomerPartyType);
      IBEUtil.log(CLASS, METHOD,
        "billtoCustomerPartyId is " + billtoCustomerPartyId);
}
      if(isSharee)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is sharee, resetting billtocustomer name");
}
        billtoCustomerName = idNameType[1];
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "billtoCustomerName is " + billtoCustomerName);

}
        /*
         * If the bill to party id is not the same as the party id of the bill to (customer)
         * it means that we are shipping to a contact. Then set the contact party id to be the
         * party id in the bill to record
         */

      }

      if((headerRec.invoice_to_party_id != null)
              && (!headerRec.invoice_to_party_id.equals(billtoCustomerPartyId.toString())))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "billtoContactPartyId exists in DB");
}
        billtoContactPartyId = headerRec.invoice_to_party_id.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoContactPartyId is " + billtoContactPartyId);
}
        billtoContactName =
          IBEUtil.nonNull(headerRec.invoice_to_contact_first_name) + " "
          + IBEUtil.nonNull(headerRec.invoice_to_contact_last_name);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoContactName is " + billtoContactName);
}
      }

      if((!billtoCustomerAccountId.equals(EMPTY_STRING))
              && (!billtoCustomerAccountId.equals(customerAccountId))
              && ((headerRec.invoice_to_party_site_id == null)
                  || (headerRec.invoice_to_party_site_id == gMissNum))
              && loadControlRec.defaultPreferences)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "No Bill to addrs exists in DB");
}
        BigDecimal primaryAddrId =
          AddressManager.getPrimaryAddressId(new BigDecimal(billtoCustomerPartyId),
                                        Address.BILL_TO, RequestCtx.getOperatingUnit());
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling AddressManager.getPrimaryAddressId");
}
        if(primaryAddrId != null) 
        {
          billtoPartySiteId = primaryAddrId.toString();
        }
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoPartySiteId is " + billtoPartySiteId);
}
      }

      if(!billtoPartySiteId.equals(EMPTY_STRING))
      {
        /*
         * if the shopCart has a bill to address, then we need to determine the
         * onwer of the address. If the owner is the same as the customer,
         * then set the billto party site type to customer, else set it to contact
         */

        String partyIdOfSite = ShoppingCartUtil.getPartyIdOfPartySite(billtoPartySiteId);
        if(billtoCustomerPartyId.equals(partyIdOfSite))
        {
          billtoPartySiteType = "customer";
        }
        else
        {
          billtoPartySiteType = "contact";
        }
      }

      if(logEnabled) { IBEUtil.log(CLASS, METHOD,  "billtoPartySiteType is " + billtoPartySiteType);
}
      if((bizCust || salesRep) && (!billtoContactPartyId.equals("")))
      {
        String[]  emailAndPhone = getPrimaryEmailAndPhone(billtoContactPartyId);
        billtoContactEmail = IBEUtil.nonNull(emailAndPhone[3]);
        billtoContactPhone = IBEUtil.nonNull(emailAndPhone[0]);
        if(logEnabled) 
{
IBEUtil.log(CLASS, METHOD,"billtoContactEmail is " + billtoContactEmail);
        IBEUtil.log(CLASS, METHOD,  "billtoContactPhone is " + billtoContactPhone);
         }
      }
    } // end if loadbilling

//--------------------------------fillTax---------------------------------//
    if (fillTax) {

      if((headerTaxRecord != null) && (headerTaxRecord.tax_detail_id != null))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "tax rcrd exists");
}
        taxDetailId = headerTaxRecord.tax_detail_id.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "taxDetailId is" + taxDetailId);
}
        taxExemptFlag = IBEUtil.nonNull(headerTaxRecord.tax_exempt_flag);
        taxReasonCode = IBEUtil.nonNull(headerTaxRecord.tax_exempt_reason_code);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "taxExemptFlag is" + taxExemptFlag);
        IBEUtil.log(CLASS, METHOD, "taxReasonCode is" + taxReasonCode);
}
        taxCertificateNumber =
          IBEUtil.nonNull(headerTaxRecord.tax_exempt_number);
      }
    } // end if fillTax
    
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Loads payment and shipping information for the cart. The boolean flag
   * parameters indicate if information is to be loaded for the cart items (line level).
   * <ul><li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The ship to customer will be the same as the sold to customer
   * if there is no ship to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is shipping to himself,
   * i.e if no ship to account id is specified and there is no ship to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the ship to address as
   * the primary shipping address, if any, of the customer.
   * <li> If the ship to customer is different from the sold to customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * ship to customer.
   * <li> If the shopCart has a ship to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If line shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * <BR>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The bill to customer will be the same as the sold to customer
   * if there is no bill to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is billing to himself,
   * i.e if no bill to account id is specified and there is no bill to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the bill to address as
   * the primary billing address, if any, of the customer.
   * <li> If the bill to customer is different from the sold to customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * bill to customer.
   * <li> If the shopCart has a bill to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * Note: Line level tax details are not loaded by this API.
   * @param cartId - the cart id
   * @param loadLineShipment - flag to indicate if line level shipping information
   * is to be loaded.
   * @param loadLinePayment - flag to indicate if line level payment information
   * is to be loaded.(<B> This feature is to be implemented </B>).
   * @param loadOrderedCart - flag to indicate if ordered cart should be loaded.
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems      = true;<BR>
    loadControlRec.headerBilling  = true;<BR>
    loadControlRec.headerPayment  = true;<BR>
    loadControlRec.headerTax      = true;<BR>
    loadControlRec.headerShipping = true;<BR>
    loadControlRec.showPrice      = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineShipping   = loadLineShipment;<BR>
    loadControlRec.includeOrdered = loadOrderedCart;<BR>
    loadControlRec.lineTax        = false;<BR>
    loadControlRec.headerContract = true;<BR>
    loadControlRec.lineBilling    = loadLineBilling;<BR>
    loadControlRec.loadType       = LOAD_CART;<BR>
    loadControlRec.defaultPreferences  = true;
   */
  public static final ShoppingCart loadWithPaymentAndShipment(String cartId,
          boolean loadLineShipment, boolean loadLineBilling,
          boolean loadOrderedCart) throws FrameworkException, SQLException, 
                                          QuoteException, 
                                          ShoppingCartException
  {
    return loadWithPaymentAndShipment(cartId, loadLineShipment, 
                                      loadLineBilling, loadOrderedCart, 
                                      false);
  }

  /**
   * Loads payment and shipping information for the cart. The boolean flag
   * parameters indicate if information is to be loaded for the cart items (line level).
   * <ul><li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The ship to customer will be the same as the sold to customer
   * if there is no ship to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is shipping to himself,
   * i.e if no ship to account id is specified and there is no ship to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the ship to address as
   * the primary shipping address, if any, of the customer.
   * <li> If the ship to customer is different from the sold to customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * ship to customer.
   * <li> If the shopCart has a ship to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If line shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * <BR>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The bill to customer will be the same as the sold to customer
   * if there is no bill to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is billing to himself,
   * i.e if no bill to account id is specified and there is no bill to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the bill to address as
   * the primary billing address, if any, of the customer.
   * <li> If the bill to customer is different from the sold to customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * bill to customer.
   * <li> If the shopCart has a bill to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * @param cartId - the cart id
   * @param loadLineShipment - flag to indicate if line level shipping information
   * is to be loaded.
   * @param loadLinePayment - flag to indicate if line level payment information
   * is to be loaded.(<B> This feature is to be implemented </B>).
   * @param loadOrderedCart - flag to indicate if ordered cart should be loaded.
   * @param loadLineTaxInfo - flag to indicate if line level tax details should
   * be loaded.
   * @return ShoppingCart
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems      = true;<BR>
    loadControlRec.headerBilling  = true;<BR>
    loadControlRec.headerPayment  = true;<BR>
    loadControlRec.headerTax      = true;<BR>
    loadControlRec.headerShipping = true;<BR>
    loadControlRec.showPrice      = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineShipping   = loadLineShipment;<BR>
    loadControlRec.includeOrdered = loadOrderedCart;<BR>
    loadControlRec.lineTax        = loadLineTaxInfo;<BR>
    loadControlRec.headerContract = true;<BR>
    loadControlRec.lineBilling    = loadLineBilling;<BR>
    loadControlRec.loadType       = LOAD_CART;<BR>
    loadControlRec.defaultPreferences  = true;
   */
  public static final ShoppingCart loadWithPaymentAndShipment(String cartId,
          boolean loadLineShipment, boolean loadLineBilling,
          boolean loadOrderedCart, 
          boolean loadLineTaxInfo) throws FrameworkException, SQLException, 
                                          QuoteException,
                                          ShoppingCartException
  {
    return loadWithPaymentAndShipment(cartId, loadLineShipment,
                                      loadLineBilling, loadOrderedCart,
                                      loadLineTaxInfo, true);
  }

/**
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems      = true;<BR>
    loadControlRec.headerBilling  = true;<BR>
    loadControlRec.headerPayment  = true;<BR>
    loadControlRec.headerTax      = true;<BR>
    loadControlRec.headerShipping = true;<BR>
    loadControlRec.showPrice      = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineShipping   = loadLineShipment;<BR>
    loadControlRec.includeOrdered = loadOrderedCart;<BR>
    loadControlRec.lineTax        = loadLineTaxInfo;<BR>
    loadControlRec.headerContract = isContractCart;<BR>
    loadControlRec.lineBilling    = loadLineBilling;<BR>
    loadControlRec.loadType       = LOAD_CART;<BR>
    loadControlRec.defaultPreferences  = true;
  */
  public static final ShoppingCart loadWithPaymentAndShipment(String cartId,
        boolean loadLineShipment, boolean loadLineBilling, boolean loadOrderedCart,
        boolean loadLineTaxInfo, boolean isContractCart)
  throws FrameworkException, SQLException,QuoteException, ShoppingCartException
  {
    return loadWithPaymentAndShipment( cartId,
           loadLineShipment,  loadLineBilling,
           loadOrderedCart,  loadLineTaxInfo,  isContractCart,  LOAD_CART);
  }

/**
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems      = true;<BR>
    loadControlRec.headerBilling  = true;<BR>
    loadControlRec.headerPayment  = true;<BR>
    loadControlRec.headerTax      = true;<BR>
    loadControlRec.headerShipping = true;<BR>
    loadControlRec.showPrice      = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineShipping   = loadLineShipment;<BR>
    loadControlRec.includeOrdered = loadOrderedCart;<BR>
    loadControlRec.lineTax        = loadLineTaxInfo;<BR>
    loadControlRec.headerContract = isContractCart;<BR>
    loadControlRec.lineBilling    = loadLineBilling;<BR>
    loadControlRec.loadType       = loadType;<BR>
    loadControlRec.defaultPreferences  = true;
  */
  public static final ShoppingCart loadWithPaymentAndShipment(String cartId,
          boolean loadLineShipment, boolean loadLineBilling,
          boolean loadOrderedCart, boolean loadLineTaxInfo, boolean isContractCart, int loadType)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    return loadWithPaymentAndShipment( cartId,
           loadLineShipment,  loadLineBilling,
           loadOrderedCart,  loadLineTaxInfo,  isContractCart,  loadType, true);
  }

  /**
   * Loads payment and shipping information for the cart. The boolean flag
   * parameters indicate if information is to be loaded for the cart items (line level).
   * <ul><li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The ship to customer will be the same as the sold to customer
   * if there is no ship to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is shipping to himself,
   * i.e if no ship to account id is specified and there is no ship to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the ship to address as
   * the primary shipping address, if any, of the customer.
   * <li> If the ship to customer is different from the sold to customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * ship to customer.
   * <li> If the shopCart has a ship to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If line shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * <BR>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The bill to customer will be the same as the sold to customer
   * if there is no bill to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is billing to himself,
   * i.e if no bill to account id is specified and there is no bill to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the bill to address as
   * the primary billing address, if any, of the customer.
   * <li> If the bill to customer is different from the sold to customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * bill to customer.
   * <li> If the shopCart has a bill to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * @param cartId - the cart id
   * @param loadLineShipment - flag to indicate if line level shipping information
   * is to be loaded.
   * @param loadLinePayment - flag to indicate if line level payment information
   * is to be loaded.(<B> This feature is to be implemented </B>).
   * @param loadOrderedCart - flag to indicate if ordered cart should be loaded.
   * @param loadLineTaxInfo - flag to indicate if line level tax details should
   * be loaded.
   * @param isContractCart - flag to indicate whether the cart that is being
   * loaded is a contract cart or not. The JSP page that calls this API needs to
   * know whether the cart is a contract cart or not. This flag is currently
   * used to set the price in the cookie. If Oracle iCache is not being used,
   * then pass in a 'true' for this flag.
   * @param loadType - flag to indicate cart or quote (use LOAD_CART or LOAD_QUOTE)
   * @param defaultPreferences - flag to indicate if preferences should be defaulted.
   * @return ShoppingCart
   * 
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems      = true;<BR>
    loadControlRec.headerBilling  = true;<BR>
    loadControlRec.headerPayment  = true;<BR>
    loadControlRec.headerTax      = true;<BR>
    loadControlRec.headerShipping = true;<BR>
    loadControlRec.showPrice      = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineShipping   = loadLineShipment;<BR>
    loadControlRec.includeOrdered = loadOrderedCart;<BR>
    loadControlRec.lineTax        = loadLineTaxInfo;<BR>
    loadControlRec.headerContract = isContractCart;<BR>
    loadControlRec.lineBilling    = loadLineBilling;<BR>
    loadControlRec.loadType       = loadType;<BR>
    loadControlRec.defaultPreferences  = defaultPreferences;
   */

  public static final ShoppingCart loadWithPaymentAndShipment(String cartId,
          boolean loadLineShipment, boolean loadLineBilling,
          boolean loadOrderedCart, boolean loadLineTaxInfo, boolean isContractCart,
          int loadType, boolean defaultPreferences)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadWithPaymentAndShipment";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId =  " + cartId
      + " loadLineShipment = " + loadLineShipment
      + " loadLineBilling = " + loadLineBilling
      + " loadOrderedCart = " + loadOrderedCart
      + " loadLineTaxInfo = " + loadLineTaxInfo
      + " isContractCart = " + isContractCart
      + " loadType  = " + loadType
      + " defaultPreferences  = " + defaultPreferences );
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.loadItems      = true;
//    loadControlRec.fillChildItems      = true;
    loadControlRec.headerBilling  = true;
    loadControlRec.headerPayment  = true;
    loadControlRec.headerTax      = true;
    loadControlRec.headerShipping = true;
    loadControlRec.showPrice      = true;
    loadControlRec.defaultPreferences = true;
    loadControlRec.lineShipping   = loadLineShipment;
    loadControlRec.includeOrdered = loadOrderedCart;
    loadControlRec.lineTax        = loadLineTaxInfo;
    loadControlRec.headerContract = isContractCart;
    loadControlRec.lineBilling    = loadLineBilling;
    loadControlRec.loadType       = loadType;
    loadControlRec.defaultPreferences  = defaultPreferences;  // added for bug 2418652
    shopCart = loadAndFill(cartId, null, getRetrievalNumberString(),loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCart = " + shopCart);
    IBEUtil.log(CLASS, METHOD, "DONE");
}
    return shopCart;
  }


  /**
   * LOAD #4: Convenience method to load a cart and optionally the shipment and payment
   * information.
   * <ul><li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The ship to customer will be the same as the sold to customer
   * if there is no ship to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is shipping to himself,
   * i.e if no ship to account id is specified and there is no ship to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the ship to address as
   * the primary shipping address, if any, of the customer.
   * <li> If the ship to customer is different from the sold to customer and no
   * shipping address is specified in the database for the cart, the method
   * defaults the address to be the primary shipping address (if any) of the
   * ship to customer.
   * <li> If the shopCart has a ship to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * <li> If line shipping information is requested to be loaded, then similar
   * defaulting rules are followed as mentioned above, except that if information
   * is not present for a particular property in the database for the line (item),
   * then the information is defaulted from the header.
   * <BR>
   * <li> Contact information is loaded only for a sales rep or a B2B user.
   * <li> The bill to customer will be the same as the sold to customer
   * if there is no bill to customer account id in the database for the cart.
   * <li> If a b2b user is logged in and is billing to himself,
   * i.e if no bill to account id is specified and there is no bill to address
   * and no contact party in the shopCart,
   * the method sets the contact as the customer itself and the bill to address as
   * the primary billing address, if any, of the customer.
   * <li> If the bill to customer is different from the sold to customer and no
   * billing address is specified in the database for the cart, the method
   * defaults the address to be the primary billing address (if any) of the
   * bill to customer.
   * <li> If the shopCart has a bill to address, then we determine the
   * onwer of the address. If the owner is the same as the customer,
   * then the party site type is "customer", else it is "contact".
   * </ul>
   * @param cartId the cart id
   * @param loadShipment flag to indicate if cart (header) level shipping
   * information is to be loaded.
   * @param loadLineShipment flag to indicate if item (line) level shipping
   * information is to be loaded.
   * @param loadPayment flag to indicate if cart (header) level payment
   * information is to be loaded.
   * @param loadLinePayment flag to indicate if item (line) level payment
   * information is to be loaded.(<B> This feature is to be implemented </B>).
   * @return ShoppingCart
   *
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException the error message will indicate the nature of
   * of the error
   */
   // #4
  public static final ShoppingCart load(String cartId, boolean loadShipment,
                                        boolean loadLineShipment,
                                        boolean loadPayment,
                                        boolean loadLinePayment) throws FrameworkException,
                                        QuoteException, SQLException,
                                        ShoppingCartException
  {
    String METHOD = "load";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "START cartId: " + cartId);
    
}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.fillChildItems  = true;
    loadControlRec.loadItems       = true;
    loadControlRec.showPrice       = true;
    loadControlRec.headerShipping  = loadShipment;
    loadControlRec.lineShipping    = loadLineShipment;
    if (loadPayment) {
      // orig loadPayment flag meant all three of these
      loadControlRec.headerBilling   = true;
      loadControlRec.headerPayment   = true;
      loadControlRec.headerTax       = true;
    }
    loadControlRec.defaultPreferences = true;    
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;

    shopCart = loadAndFill(cartId, null, getRetrievalNumberString(),loadControlRec);

/*
    ShoppingCart  shopCart = new ShoppingCart(cartId, true, true, false,
                                              false, loadPayment,
                                              loadLinePayment, loadShipment,
                                              loadLineShipment, false, false,
                                              false, false, false);

    shopCart.cartId = shopCart.headerRec.quote_header_id.toString();

    if(loadShipment)
    {
//      shopCart.loadShipmentDetails();

    }

    if(loadLineShipment)
    {
//      loadItems(shopCart, loadLineShipment, loadLinePayment, false, false);

    }

    if(loadPayment)
    {
//      shopCart.loadPaymentDetails(loadLinePayment);

    }
*/

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;
  }


  /**
   * LOAD #5 - A wrapper around the ShoppingCart constructor with the same signature.<BR>
   * <BR>
   * See the Quote.load method for more information about this method.  This method won't do any of the 'fill' apis nor conversions to string except for the cartId.
   *
   *
   * @param cartId
   * @param loadLine
   * @param loadLineDetail
   * @param loadHeaderPriceAttr
   * @param loadLinePriceAttr
   * @param loadHeaderPayment
   * @param loadLinePayment
   * @param loadHeaderShipment
   * @param loadLineShipment
   * @param loadHeaderTaxDetail
   * @param loadLineTaxDetail
   * @param loadLineRel
   * @param loadLineAttrExt
   * @param includeOrdered
   * 
   * @return ShoppingCart
   * 
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException the error message will indicate the nature of
   * of the error
   */
   // #5
  public static final ShoppingCart load(String cartId, boolean loadLine,
                                        boolean loadLineDetail,
                                        boolean loadHeaderPriceAttr,
                                        boolean loadLinePriceAttr,
                                        boolean loadHeaderPayment,
                                        boolean loadLinePayment,
                                        boolean loadHeaderShipment,
                                        boolean loadLineShipment,
                                        boolean loadHeaderTaxDetail,
                                        boolean loadLineTaxDetail,
                                        boolean loadLineRel,
                                        boolean loadLineAttrExt,
                                        boolean includeOrdered) throws SQLException,
                                        FrameworkException,
                                        ShoppingCartException
  {
    ShoppingCart  shopCart = new ShoppingCart(cartId, loadLine,
                                              loadLineDetail,
                                              loadHeaderPriceAttr,
                                              loadLinePriceAttr,
                                              loadHeaderPayment,
                                              loadLinePayment,
                                              loadHeaderShipment,
                                              loadLineShipment,
                                              loadHeaderTaxDetail,
                                              loadLineTaxDetail, loadLineRel,
                                              loadLineAttrExt,
                                              includeOrdered);

    shopCart.cartId = shopCart.headerRec.quote_header_id.toString();


    return shopCart;
  }

  /*
   * public static final ShoppingCart[] loadAllCarts(boolean includeVersion)
   * throws SQLException, FrameworkException, ShoppingCartException
   * {
   * return null;
   * }
   *
   * public static final ShoppingCart[] loadVersions(String quoteNumber)
   * throws SQLException, FrameworkException, ShoppingCartException
   * {
   * return null;
   * }
   *
   * public static final QuoteAccessRecord[] loadSharees(String cartId)
   * throws SQLException, FrameworkException, ShoppingCartException
   * {
   * return null;
   * }
   */

  /**
    * TOP level for filling line details (formerly called loadItems)
    * Calls down to fillLinesAllTypes (to take care of line line at a time)
    * and other api's that take care of all lines (loadLineTaxInfo, loadCommitmentInfo)
    */
  private static void fillAllLinesAllDetails(ShoppingCart shopCart) throws FrameworkException,
                                 SQLException
  {
    String  METHOD = "fillAllLinesAllDetails";

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
         }
    boolean checkSuppFlag = true;
    boolean useSupp = IBEUtil.useFeature("IBE_USE_SUPPORT");
    // cart level support can only be on if the line service profile is off
    boolean useCartLevelSupp = !useSupp && IBEUtil.useFeature("IBE_USE_SUPPORT_CART_LEVEL");

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "useSupp is " + useSupp);
    IBEUtil.log(CLASS, METHOD, "useCartLevelSupp is " + useCartLevelSupp);
        }
    boolean useCfg = true;
    boolean useRel = useSupp || useCfg;

    int[][] outOrder = new int[1][];
    int[][] outDepths = new int[1][];
    int[]   depths = null;

//    shopCart.loadHeaderInformation();
//    shopCart.loadPriceSummaryInformation();

    if(shopCart.lineRec != null)
    {
     if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
             "fillLines called with shopCart : " + shopCart
             + " and lineRec: " + shopCart.lineRec);
      }
      int numOfCartLines = shopCart.lineRec.length;

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "in fillLines, numOfCartLines is " + numOfCartLines);
       }
      ShoppingCartItem[]  cartItems = new ShoppingCartItem[numOfCartLines];

      shopCart.shopCartItems = cartItems;

      if(useRel)
      {
        BigDecimal[]  cartLineIds = new BigDecimal[numOfCartLines];
        BigDecimal[]  cartParentLineIds = null;
        BigDecimal[]  cartChildLineIds = null;

        for(int i = 0; i < numOfCartLines; i++)
        {
          cartLineIds[i] = shopCart.lineRec[i].quote_line_id;
          if(shopCart.logEnabled)
  { 
  IBEUtil.log(CLASS, METHOD, "cartLineIds[i] is " + cartLineIds[i]);
          }
          
        }

        if(shopCart.lineRelRec != null)
        {
          cartParentLineIds = new BigDecimal[shopCart.lineRelRec.length];
          cartChildLineIds = new BigDecimal[shopCart.lineRelRec.length];
          int lineRelRecLength = shopCart.lineRelRec.length;
          if(shopCart.logEnabled) {
               IBEUtil.log(CLASS, METHOD," shopCart.lineRelRec.length is " + lineRelRecLength);
           }
          for(int i = 0; i < lineRelRecLength; i++)
          {
            if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " i is " + i);
             }
            cartParentLineIds[i] = shopCart.lineRelRec[i].quote_line_id;
            cartChildLineIds[i] =
              shopCart.lineRelRec[i].related_quote_line_id;

           if(shopCart.logEnabled)
            {
             IBEUtil.log(CLASS, METHOD, "cartParentLineIds[i]  is " + cartParentLineIds[i] );
            IBEUtil.log(CLASS, METHOD,"cartChildLineIds[i]  is " + cartChildLineIds[i]);
            }
          }
        } // end if shopCart.lineRelRec != null

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling sortParentChildRelns");
         }
        IBEUtil.sortParentChildRelns(cartLineIds, cartParentLineIds,
                                     cartChildLineIds, outOrder, outDepths);
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling sortParentBlah");
        }
        int[] testOutOrder = outOrder[0];
        int[] testOutDepths = outDepths[0];

        /*
         * for (int i = 0; i < outOrder[0].length; i++)
         * for (int i = 0; i < outDepths.length; i++)
         */

        // cart-level-support
        if(useCartLevelSupp)
        {
          boolean   checkSuppOnly = false;
          ArrayList supportVec = null;

          supportVec = new ArrayList();

          boolean startFlag = false;
          int     svaDepth = -1;

          for(int i = 0; i < numOfCartLines; i++)
          {
            int k = outOrder[0][i];

            // first serviceable item
            if("SVA".equals(shopCart.lineRec[k].item_type_code)
                    &&!shopCart.containsSVAFlag)
            {
              if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Contains SVA items");
               }
              shopCart.containsSVAFlag = true;
              startFlag = true;
              svaDepth = outDepths[0][i];

              continue;
            }

            // finish the first serviceable item
            if(startFlag && (svaDepth >= outDepths[0][i]))
            {
              if(checkSuppOnly)
              {
                break;
              }

              startFlag = false;

              continue;
            }

            // only return the service associated to the first serviceable item
            if(startFlag && "SRV".equals(shopCart.lineRec[k].item_type_code)
                    && (svaDepth + 1 == outDepths[0][i]))
            {
              supportVec.add(shopCart.lineRec[k].inventory_item_id);
            }
          }   // end of for loop

          shopCart.supportLevel = ShoppingCartUtil.getSupportLevel(shopCart,
                  supportVec);
          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
            "Support level for cart is " + shopCart.supportLevel);
            }
        } // end if useCartLevelSupp

        depths = outDepths[0];
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to call fillLinesPopulateAll");
         }
        fillLinesPopulateAll(shopCart, numOfCartLines, outOrder, depths);
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling fillLinesPopulateAll");
           }
      } // end if useRel
     
      if ( (shopCart.loadControlRec.lineTax) && (!RequestCtx.userIsSalesRep() ) )
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to get line tax info");
          }
        shopCart.fillAllLinesTaxInfo();
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling fillLineTaxInfo");
           }
      }
      if (shopCart.loadControlRec.lineCommitments) {
        shopCart.fillAllLinesCommitmentInfo();
      }
      if (shopCart.loadControlRec.lineAgreements || shopCart.loadControlRec.lineCommitments) {
        shopCart.fillAllLinesAgreementInfo();
      }
    } // shop cart lineRec is null
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
           }
  }

  /**
   * Loads the header information into various fields of the cart.
   * Is public so ShoppingCartUtil api's can call it.
   *
   * @throws FrameworkException
   * @throws SQLException
   */
  public void fillHeaderInformation() throws SQLException, FrameworkException
  {
    String METHOD = "fillHeaderInformation";

    if(!headerLoaded)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
         }
      cartId = headerRec.quote_header_id.toString();
      cartNumber = headerRec.quote_number.toString();

      versionNumber = headerRec.quote_version.toString();
      status = IBEUtil.nonNull(headerRec.quote_status);
      currencyCode = headerRec.currency_code;

      if ("IBE_PRMT_SC_UNNAMED".equals(headerRec.quote_name) || "IBE_PRMT_SC_DEFAULTNAMED".equals(headerRec.quote_name))
      {
        try
        {
          cartName = DisplayManager.getTextMediaOrFndMsg(headerRec.quote_name);
        }
        catch (MediaException e)
        {
          cartName = headerRec.quote_name;
        }
      } else {
        cartName = IBEUtil.nonNull(headerRec.quote_name);
      }


      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
      IBEUtil.log(CLASS, METHOD, "cartName = " + cartName);
      IBEUtil.log(CLASS, METHOD, "currencyCode = " + currencyCode);

}
      if((headerRec.cust_account_id != null)
              && (headerRec.cust_account_id != gMissNum))
      {
        customerAccountId = headerRec.cust_account_id.toString();
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cust accnt id is " + customerAccountId );

}
      if((headerRec.party_id != null)
              && (headerRec.party_id != gMissNum))
      {
        soldtoContactPartyId = headerRec.party_id.toString();
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "contact party id is " + soldtoContactPartyId );

}
      customerName = IBEUtil.nonNull(headerRec.party_name);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cust name is " + customerName);

}
      if(headerRec.last_update_date != null)
      {
        lastModifiedTimestamp = headerRec.last_update_date.toString();
        lastModifiedDate =
          ShoppingCartUtil.formatTimestamp(headerRec.last_update_date);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "last modf ts is " + lastModifiedTimestamp);
}
      }

      if(headerRec.quote_expiration_date != null)
      {
        expirationTimestamp = headerRec.quote_expiration_date.toString();
        expirationDate =
          ShoppingCartUtil.formatTimestamp(headerRec.quote_expiration_date);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "exp ts is " + expirationTimestamp);
}
      }

      if(headerRec.creation_date != null)
      {
        creationDate = 
          ShoppingCartUtil.formatTimestamp(headerRec.creation_date);
      }
      // eventually, supposed to be based on publish_flag instead.
      if((headerRec.resource_id != null) 
              && (headerRec.resource_id != gMissNum))
      {
        isPublished = true;
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart has been published");
         }
      }

      if((headerRec.contract_template_id != null)
              && (headerRec.contract_template_id != gMissNum))
      {
        this.contract_template_id = headerRec.contract_template_id.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart has contract_template_id : " + contract_template_id);
          }
      }

      if((headerRec.contract_template_major_ver != null)
              && (headerRec.contract_template_major_ver != gMissNum))
      {
        this.contract_template_major_ver = headerRec.contract_template_major_ver.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart has contract_template_major_ver : " + contract_template_major_ver);
         }
      }
      if(headerRec.minisite_id != null)
      {
          this.minisiteId = headerRec.minisite_id.toString();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Minisite_id is :"+this.minisiteId);
}
      }

      headerLoaded = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
         }
    }
  }

  /**
   * Method declaration
   *
   *
   * @throws FrameworkException
   * @throws SQLException
   */
  private void fillSoldtoInformation() throws SQLException, FrameworkException
  {
    String METHOD = "fillSoldtoInformation";

    if(!soldtoInfoLoaded)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
        }
      soldtoContactName = IBEUtil.nonNull(headerRec.person_first_name)
                          + " "
                          + IBEUtil.nonNull(headerRec.person_last_name);

      String[]  contactPoints = new String[2];

      if(headerRec.party_id != null)
      {
        contactPoints =
          getPrimaryEmailAndPhone(headerRec.party_id.toString());
      }

      soldtoContactEmail = contactPoints[3];
      soldtoContactPhone = contactPoints[0];

      if(logEnabled) { 
  IBEUtil.log(CLASS, METHOD, "soldtoContactName = " + soldtoContactName);
      IBEUtil.log(CLASS, METHOD, "soldtoContactEmail = " + soldtoContactEmail);
       IBEUtil.log(CLASS, METHOD, "soldtoContactPhone = " + soldtoContactPhone);
       }
      soldtoInfoLoaded = true;
    }
  }

  /**
   * Method declaration
   * 
   * 
   * @throws FrameworkException
   * @throws SQLException
   */
  private void fillPriceSummaryInformation()
          throws SQLException, FrameworkException
  {
    if(!summaryLoaded)
    {
      String METHOD = "fillPriceSummaryInformation";
      if(logEnabled) {
        IBEUtil.log(CLASS, METHOD, "CALLED");
        IBEUtil.log(CLASS, METHOD, "loadControlRec.formatNetPrices: " + loadControlRec.formatNetPrices);
}
      // load subtotal, s&h, total
      currencyCode = headerRec.currency_code;

      if((headerRec.total_shipping_charge != null) && loadControlRec.formatNetPrices)
      {
        double  doubleShippingAndHandling =
          headerRec.total_shipping_charge.doubleValue();

        shippingAndHandling = PriceObject.formatNumber(currencyCode,
                doubleShippingAndHandling);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "shippingAndHandling is " + shippingAndHandling);
}
      }

      if((headerRec.total_tax != null) && loadControlRec.formatNetPrices)
      {
        double  doubleTax = headerRec.total_tax.doubleValue();

        tax = PriceObject.formatNumber(currencyCode, doubleTax);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,"tax is " + tax);
}
      }

      double  doubleTotalListPrice = 0.0;
      double  doubleTotalQuotePrice = 0.0;
      double  doubleTotalAdjustedAmount = 0.0;

      if((headerRec.total_adjusted_amount != null) && loadControlRec.formatNetPrices)
      {
        doubleTotalAdjustedAmount =
          headerRec.total_adjusted_amount.doubleValue();
        totalDiscounts = PriceObject.formatNumber(currencyCode,
                                                  doubleTotalAdjustedAmount);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,"totalDiscounts is " + totalDiscounts);
}
      }

      if(headerRec.total_list_price != null)
      {
        doubleTotalListPrice = headerRec.total_list_price.doubleValue();
        totalListPrice = PriceObject.formatNumber(currencyCode,
                                                  doubleTotalListPrice);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "totalListPrice is " + totalListPrice);
}
      }

      if(((headerRec.total_list_price != null)
              && (headerRec.total_adjusted_amount != null)) && loadControlRec.formatNetPrices)
      {
        double  doubleSubTotalPrice = doubleTotalListPrice 
                                      + doubleTotalAdjustedAmount;

        subTotalPrice = PriceObject.formatNumber(currencyCode,
                                                 doubleSubTotalPrice);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "subTotalPrice is " + subTotalPrice);
}
      }

      if((headerRec.total_quote_price != null) && loadControlRec.formatNetPrices)
      {
        doubleTotalQuotePrice = headerRec.total_quote_price.doubleValue();
        totalPrice = PriceObject.formatNumber(currencyCode,
                                              doubleTotalQuotePrice);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "totalPrice is " + totalPrice);
}
      }

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling getTaxInfo for header");

}
      if ( !RequestCtx.userIsSalesRep() && loadControlRec.headerTaxCharges)
        taxInfo = TaxInfo.getTaxInfo(cartId, currencyCode);
      summaryLoaded = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
    }
  }

  /**
   * Branches out to different populate methods
   * (formerly getLineInfo)
   */
  private static void fillLinesPopulateAll(ShoppingCart shopCart, int numOfCartLines,
                                  int[][] outOrder, int[] depths)
                                  throws FrameworkException, SQLException
  {
    String            METHOD = "fillLinesPopulateAll";
    boolean           isCfgItem = false;
    boolean           isSvaItem = false;
    boolean           isBdlItem = false;    
    ShoppingCartItem  cartItem = null;

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                "fillLinesPopulateAll called with shopCart: " + shopCart
                + " and numLines : " + numOfCartLines);

}
    ArrayList shopCartItemsArray = new ArrayList();
    ArrayList shopCartItemsPRGArray = new ArrayList();
    int       i = 0, j = 0;

    if(shopCart.lineShipmentRec == null)
    {
      shopCart.lineShipmentRec = new ShipmentRecord[numOfCartLines];
    }

    while(i < numOfCartLines)
    {
      cartItem = new ShoppingCartItem();
      int lineIndex = outOrder[0][i];

      if(shopCart.logEnabled)  {
        IBEUtil.log(CLASS, METHOD,"------------ iteration " + i + " cartItem is " + cartItem + " --------------");
        IBEUtil.log(CLASS, METHOD,"  line index from outOrder is:  " + lineIndex);
        IBEUtil.log(CLASS, METHOD,"  lineRec is     : " + shopCart.lineRec[lineIndex]);
        IBEUtil.log(CLASS, METHOD,"  quoteLineId is : "+ shopCart.lineRec[lineIndex].quote_line_id);
        IBEUtil.log(CLASS, METHOD,"  itemId is      : "+ shopCart.lineRec[lineIndex].inventory_item_id);
        IBEUtil.log(CLASS, METHOD,"  itemTypeCode is: "+ shopCart.lineRec[lineIndex].item_type_code);
      }

      cartItem.itemType =
        IBEUtil.nonNull(shopCart.lineRec[lineIndex].item_type_code);
      boolean isBundle = false;
      try
      {
        Item item = Item.load((shopCart.lineRec[lineIndex].inventory_item_id).intValue(), Item.SHALLOW, false,false);
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Item loaded");
}
        isBundle = item.isModelBundle();
      }
      catch(ItemNotFoundException e)
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Item not found!");
}
      }

      if(depths[i] == 0)
      {
        if("MDL".equals(cartItem.itemType) && !isBundle)
        {
          isCfgItem = true;
        }
        else
        {
          isCfgItem = false;
        }

        if("MDL".equals(cartItem.itemType) && isBundle)
        {
          isBdlItem = true;
        }
        else
        {
          isBdlItem = false;
        }

        if("SVA".equals(cartItem.itemType))
        {
          isSvaItem = true;
        }
        else
        {
          isSvaItem = false;
        }
      }
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Item flags: ");
      IBEUtil.log(CLASS, METHOD, "    isCfgItem: " + isCfgItem);
      IBEUtil.log(CLASS, METHOD, "    isSvaItem: " + isSvaItem);
      IBEUtil.log(CLASS, METHOD, "    isBdlItem: " + isBdlItem);
}
      
      if(isCfgItem)
      {
        i = fillPopulateConfigOrBundle(shopCart, numOfCartLines, cartItem,
                                numOfCartLines, i, outOrder, depths, CONFIG_CHILDREN);
      }
      else if(isSvaItem)
      {
        i = fillPopulateService(shopCart, numOfCartLines, cartItem,
                                 numOfCartLines, i, outOrder, depths);
      }
      else if(isBdlItem)
      {
        i = fillPopulateConfigOrBundle(shopCart, numOfCartLines, cartItem,
                                 numOfCartLines, i, outOrder, depths, BUNDLE_CHILDREN);
      }
      else
      {
        if (!("F".equals(shopCart.lineRec[lineIndex].pricing_line_type_indicator) && !shopCart.loadControlRec.loadPRGItems)) {
          fillOneLineAllDetails(shopCart, cartItem, lineIndex, false, false, false, 1,
                         null, null, null, null, null, null, EMPTY_STRING);
        } else {
          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " Skipping fillOneLineAllDetais call for PRG Item since loadPRGItems is false");}
        }
        i++;
      }

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " Going into items array is " + cartItem);}
      if (!"F".equals(shopCart.lineRec[lineIndex].pricing_line_type_indicator)) {
        shopCartItemsArray.add(cartItem);
      } else if (shopCart.loadControlRec.loadPRGItems) {
        // if the pricing indicator is F and the flag says so, save the PRG item; else skip it.
        shopCartItemsPRGArray.add(cartItem);
      }
    } // end of while loop over loaded items

    /******* now that we're looped over all the items, populate the non PRG top level ShoppingCartItem array ******/
    int arraySize = shopCartItemsArray.size();
    if(shopCart.logEnabled) {
      IBEUtil.log(CLASS, METHOD, " NON PRG Array size is " + arraySize);
    }
    shopCart.shopCartItems = new ShoppingCartItem[arraySize];
    for(int index = 0; index < arraySize; index++)
    {
      ShoppingCartItem  scartitem =
        (ShoppingCartItem) shopCartItemsArray.get(index);

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                  " Putting into shop cart items array is " + scartitem);
      }
      shopCart.shopCartItems[index] = scartitem;
    }

    /******* if set to do so, populate the PRG ShoppingCartItem array ******/
    if (shopCart.loadControlRec.loadPRGItems) {
      int prgArraySize = shopCartItemsPRGArray.size();
      if(shopCart.logEnabled) {
        IBEUtil.log(CLASS, METHOD, " PRG Array size is " + prgArraySize);
      }
      if (prgArraySize > 0)
      {
       shopCart.shopCartPRGItems = new ShoppingCartItem[prgArraySize];
       for(int index = 0; index < prgArraySize; index++)
       {
         ShoppingCartItem  scartitemPRG =
           (ShoppingCartItem) shopCartItemsPRGArray.get(index);

         if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                     " Putting into PRG shop cart items array is " + scartitemPRG);
         }
         shopCart.shopCartPRGItems[index] = scartitemPRG;
         if (scartitemPRG.qualifyingItemIds != null)
         {
           for (int q = 0; q < scartitemPRG.qualifyingItemIds.length; q++)
           {
             if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                     " scartitemPRG.qualifyingItemIds[" + q + "]: " + scartitemPRG.qualifyingItemIds[q]);
             }
           }
         }
       } // end for loop that populates the prg items
      } // end if prgArraySize > 0
    } // end PRG items

  }

  /**
   * Method declaration
   * 
   * 
   * @param shopCart
   * @param numCartLines
   * @param cartItem
   * @param loadLineShipments
   * @param loadLinePayments
   * @param numOfCartLines
   * @param i
   * @param outOrder
   * @param depths
   * @param childType (either BUNDLE_CHILDREN or CONFIG_CHILDREN)
   * 
   * @return
   * 
   * @throws FrameworkException
   * @throws SQLException
   *
   * @see
   */
  private static int fillPopulateConfigOrBundle(ShoppingCart shopCart,
                                         int numCartLines, 
                                         ShoppingCartItem cartItem, 
                                         int numOfCartLines, int i, 
                                         int[][] outOrder, 
                                         int[] depths,
                                         int childType) throws FrameworkException,
                                         SQLException
  {
    String    METHOD = "fillPopulateConfigOrBundle";
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    if (childType == CONFIG_CHILDREN)
  {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " childType is CONFIG_CHILDREN");
      }
  } //if
    else if (childType == BUNDLE_CHILDREN)
  {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " childType is BUNDLE_CHILDREN");
      }
  } //elsif

    double[]  dUnitListPrice = new double[1];
    double[]  dUnitDiscount = new double[1];
    double[]  dUnitNetPrice = new double[1];
    double[]  dTotalListPrice = new double[1];
    double[]  dTotalDiscount = new double[1];
    double[]  dTotalNetPrice = new double[1];

    // populate the model first
    int       origIndex = outOrder[0][i];
    int       lineIndex = outOrder[0][i];
    int       stackIndex = 0;
    int       depthsLength = depths.length;

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " depthsLength is  " + depthsLength);

}
    String[]  parentIdArray = new String[depthsLength];
    String    parentItemId = EMPTY_STRING;

    parentIdArray[stackIndex] = parentItemId;
    // IBEUtil.log(CLASS, METHOD, "Item id of MDL is " + parentIdArray[stackIndex] );

    dUnitListPrice[0] = 0;
    dUnitDiscount[0] = 0;
    dUnitNetPrice[0] = 0;
    dTotalListPrice[0] = 0;
    dTotalDiscount[0] = 0;
    dTotalNetPrice[0] = 0;

    double  modelQuantity = shopCart.lineRec[lineIndex].quantity.doubleValue();

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Populating model line");
}
    fillOneLineAllDetails(shopCart, cartItem,outOrder[0][i], true, true, false,modelQuantity,
                     dUnitListPrice, dUnitDiscount, dUnitNetPrice, dTotalListPrice, dTotalDiscount, dTotalNetPrice,
                     EMPTY_STRING);
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "MDL Item id is " + cartItem.inventoryItemId);
}
    i++;

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " modelQuantity is " + modelQuantity);
    IBEUtil.log(CLASS, METHOD, " i is " + i);
}
    // init populate the inidividual config items
    ArrayList childItems = new ArrayList();
    ArrayList serviceItems = new ArrayList();

    while((i < numCartLines) && (depths[i] > 0))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " depths[i] is  " + depths[i]);
      IBEUtil.log(CLASS, METHOD, " depths[i - 1] is  " + depths[i - 1]);
}
      lineIndex = outOrder[0][i];

      if(depths[i] > depths[i - 1])
      {
        stackIndex++;

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                    "Depth increased, now stackIndex is  " + stackIndex);

}
        int parentItemIndex = outOrder[0][i - 1];

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "parentItemIndex is  " + parentItemIndex);

}
        parentItemId =
          shopCart.lineRec[parentItemIndex].inventory_item_id.toString();

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " parentItemId is  " + parentItemId);

}
        parentIdArray[stackIndex] = parentItemId;

      }
      else if(depths[i] < depths[i - 1])
      {
        stackIndex = depths[i - 1] - (depths[i - 1] - depths[i]);

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                    "After decrementing stackIndex is  " + stackIndex);

}
        parentItemId = parentIdArray[stackIndex];

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " parentItemId is  " + parentItemId);
}
      }



      if(ShoppingCartItem.SERVICE_ITEM_TYPE.equals(shopCart.lineRec[lineIndex].item_type_code))
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Found a service");
}
        ServiceItem serviceItem = new ServiceItem();

        fillOneLineAllDetails(shopCart, serviceItem, lineIndex, true, false, true, modelQuantity,
                         dUnitListPrice, dUnitDiscount, dUnitNetPrice, dTotalListPrice, dTotalDiscount, dTotalNetPrice, parentItemId);
        serviceItems.add(serviceItem);
        if (!serviceItem.isOrderable()) cartItem.containsUnorderableChildren = true;
      }
      else
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Must be a child item");
}
        ConfigurationItem configItem = null;
        BundledItem bundleItem = null;
        if (childType == CONFIG_CHILDREN) {
          configItem = new ConfigurationItem();
          // the quantity and prices of the configuration items should be adjusted
          // according to the model quantity
          fillOneLineAllDetails(shopCart, configItem, lineIndex, true, false, false,
                           modelQuantity, dUnitListPrice, dUnitDiscount,
                           dUnitNetPrice, dTotalListPrice, dTotalDiscount,
                           dTotalNetPrice, parentItemId);
          childItems.add(configItem);
          if (!configItem.isOrderable()) cartItem.containsUnorderableChildren = true;
        }
        else if (childType == BUNDLE_CHILDREN) {
          bundleItem = new BundledItem();

          // the quantity and prices of the configuration items should be adjusted
          // according to the model quantity
          fillOneLineAllDetails(shopCart, bundleItem, lineIndex, true, false, false,
                           modelQuantity, dUnitListPrice, dUnitDiscount,
                           dUnitNetPrice, dTotalListPrice, dTotalDiscount,
                           dTotalNetPrice, parentItemId);
          childItems.add(bundleItem);
          if (!bundleItem.isOrderable()) cartItem.containsUnorderableChildren = true;
        }
      }
      i++;
    }

    int carraySize = childItems.size();
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Number of children in MDL is " + carraySize);
}
    /* bug#4041867*/
    if (shopCart.isOrderable() && carraySize == 0)
    {
      String cfgHdrId = cartItem.getConfigHeaderId();
      String cfgRevNum = cartItem.getConfigRevisionNumber();
      if(cfgHdrId==null || cfgHdrId.trim().length()==0 || cfgRevNum==null || cfgRevNum.trim().length()==0) 
        shopCart.isOrderable=false;
    }
    if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, "shopCart orderable " + shopCart.isOrderable());
    if (childType == CONFIG_CHILDREN) {
      cartItem.cfgItems = new ConfigurationItem[carraySize];
      for(int index = 0; index < carraySize; index++)
      {
        cartItem.cfgItems[index] =
          (ConfigurationItem) childItems.get(index);
      }
    } else if (childType == BUNDLE_CHILDREN) {
      cartItem.bdlItems = new BundledItem[carraySize];
      for(int index = 0; index < carraySize; index++)
      {
        cartItem.bdlItems[index] =
          (BundledItem) childItems.get(index);
      }
    }

    int sarraySize = serviceItems.size();

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
                "Number of service items in MDL is " + sarraySize);

}
    cartItem.svcItems = new ServiceItem[sarraySize];

    for(int index = 0; index < sarraySize; index++)
    {
      cartItem.svcItems[index] = (ServiceItem) serviceItems.get(index);
    }

    String  currencyCode = null;

    if((shopCart.lineRec[origIndex].currency_code == null)
            || ((shopCart.lineRec[origIndex].currency_code).trim().length()
                == 0))
    {

      // use cart-level currency_code
      currencyCode = shopCart.headerRec.currency_code;
    }
    else
    {
      currencyCode = shopCart.lineRec[origIndex].currency_code;
    }

    cartItem.cfgUnitListPrice = PriceObject.formatNumber(currencyCode,
            dUnitListPrice[0]);
    cartItem.cfgTotalListPrice = PriceObject.formatNumber(currencyCode,
            dTotalListPrice[0]);

    if (shopCart.loadControlRec.formatNetPrices) {
      cartItem.cfgUnitDiscount = PriceObject.formatNumber(currencyCode,
            dUnitDiscount[0]);
      cartItem.cfgUnitNetPrice = PriceObject.formatNumber(currencyCode,
            dUnitNetPrice[0]);
      cartItem.cfgTotalDiscount = PriceObject.formatNumber(currencyCode,
            dTotalDiscount[0]);
      cartItem.cfgTotalNetPrice = PriceObject.formatNumber(currencyCode,
            dTotalNetPrice[0]);
    } else {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " NOT FORMATTING NET CONFIG PRICES ");
      }
    }

    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());
    /*bug#4469168: modelQty is always 1 for top model*/
    //cartItem.cfgQuantity = numFormat.formatNumber(shopCart.lineRec[origIndex].quantity);

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "(model line) cartItem.containsUnorderableChildren " + cartItem.containsUnorderableChildren);
    IBEUtil.log(CLASS, METHOD, "while RETURNING i is " + i);
    IBEUtil.log(CLASS, METHOD, "DONE");
    }
    return i;
  }

  /**
   * Method declaration
   *
   *
   * @param shopCart
   * @param numCartLines
   * @param cartItem
   * @param numOfCartLines
   * @param i
   * @param outOrder
   * @param depths
   * 
   * @return
   * 
   * @throws FrameworkException
   * @throws SQLException
   * 
   * @see
   */
  private static int fillPopulateService(ShoppingCart shopCart,
                                          int numCartLines, 
                                          ShoppingCartItem cartItem, 
                                          int numOfCartLines, int i, 
                                          int[][] outOrder, 
                                          int[] depths) throws FrameworkException, 
                                          SQLException
  {
    String  METHOD = "fillPopulateService";
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " i is " + i);
}
    // populate the model first
//    int     origIndex = i;
    double  modelQuantity =
      shopCart.lineRec[outOrder[0][i]].quantity.doubleValue();
    int     lineIndex = outOrder[0][i];

    fillOneLineAllDetails(shopCart, cartItem, lineIndex, false, false, false, 1, null, null, null,
                     null, null, null, EMPTY_STRING);
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "SVA Item id is " + cartItem.inventoryItemId);
}
    i++;

    // init populate the inidividual config items
    ArrayList svcItems = new ArrayList();

    while((i < numCartLines) && (depths[i] > 0))
    {
      lineIndex = outOrder[0][i];

      ServiceItem svcItem = new ServiceItem();

      // the quantity and prices of the configuration items should be adjusted
      // according to the model quantity
      fillOneLineAllDetails(shopCart, svcItem, lineIndex, false, false, true, 1,
                       null, null, null, null, null, null,
                       cartItem.inventoryItemId);
      svcItems.add(svcItem);
      if (!svcItem.isOrderable()) cartItem.containsUnorderableChildren = true;
      i++;
    }

    int arraySize = svcItems.size();
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Num service items is " + arraySize);
}
    cartItem.svcItems = new ServiceItem[arraySize];

    for(int index = 0; index < arraySize; index++)
    {
      cartItem.svcItems[index] = (ServiceItem) svcItems.get(index);
    }

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "(serviceable line) cartItem.containsUnorderableChildren " + cartItem.containsUnorderableChildren);
    IBEUtil.log(CLASS, METHOD, "DONE, while RETURNING i is " + i);
}

    return i;
  }

  /**
   * Method declaration
   *
   * ALL Lines will bubble down to this api.
   * showPrice & fillChildItems flags get used in here
   *
   * @param shopCart
   * @param cartItem
   * @param lineIndex
   * @param isCfgItem
   * @param isMdlItem
   * @param isSvcItem
   * @param modelQuantity
   * @param dUnitListPrice
   * @param dUnitDiscount
   * @param dUnitNetPrice
   * @param dTotalListPrice
   * @param dTotalDiscount
   * @param dTotalNetPrice
   *
   * @throws FrameworkException
   * @throws SQLException
   *
   * @see
   */
  private static void fillOneLineAllDetails(ShoppingCart shopCart,
                                       ShoppingCartItem cartItem,
                                       int lineIndex, boolean isCfgItem,
                                       boolean isMdlItem, boolean isSvcItem,
                                       double modelQuantity,
                                       double[] dUnitListPrice,
                                       double[] dUnitDiscount,
                                       double[] dUnitNetPrice,
                                       double[] dTotalListPrice,
                                       double[] dTotalDiscount,
                                       double[] dTotalNetPrice,
                                       String parentItemId) throws FrameworkException,
                                       SQLException
  {
    String  METHOD = "fillOneLineAllDetails";
    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
      IBEUtil.log(CLASS, METHOD, " isCfgItem  " + isCfgItem);
      IBEUtil.log(CLASS, METHOD, " isMdlItem  " + isMdlItem);
      IBEUtil.log(CLASS, METHOD, " lineIndex  " + lineIndex);
    }
    if (EMPTY_STRING.equals(parentItemId))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " parentItemId  " + parentItemId + " PARENT ITEM");
      }
    }//if
    else
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " parentItemId  " + parentItemId + " CHILD ITEM");
      }
    } //else

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
      "inventory_item_id is " + shopCart.lineRec[lineIndex].inventory_item_id );
    }
    // no parentItemId means it's a child item
    if (shopCart.loadControlRec.loadItems &&
        (EMPTY_STRING.equals(parentItemId) || isSvcItem ||
        (!EMPTY_STRING.equals(parentItemId) && shopCart.loadControlRec.fillChildItems)))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  loading Item info");
      }
      try
      {
        //New Item.load call to load published/unpublished item details
        Item  item =
          Item.load((shopCart.lineRec[lineIndex].inventory_item_id).intValue(), Item.SHALLOW, false,false);

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Item loaded");
          IBEUtil.log(CLASS, METHOD, "item.getDescription(): " + item.getDescription());
          IBEUtil.log(CLASS, METHOD, "cartItem : " + cartItem);
        }
        cartItem.description = item.getDescription();
        cartItem.partNumber = IBEUtil.nonNull(item.getPartNumber());
        cartItem.isOrderable = item.isOrderable() && !(Item.DISABLED.equals(item.getWebStatus()));
        if(!(Item.PUBLISHED.equals(item.getWebStatus())))cartItem.isPublished = false;
        cartItem.isBundle = item.isModelBundle();
        cartItem.isServiceable = item.isServiceable();

        // added 6/30/02
        cartItem.uomName = item.getUOM(shopCart.lineRec[lineIndex].uom_code);
      }
      catch(ItemNotFoundException e)
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "ItemNotFoundException while loading Published+Unpublished item");
        }
        cartItem.description = EMPTY_STRING;
        cartItem.partNumber = EMPTY_STRING;
        cartItem.isOrderable = false;
        cartItem.isBundle = false;
        // added 6/30/02
        cartItem.uomName = EMPTY_STRING;
      }
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  cartItem.description " + cartItem.description);
        IBEUtil.log(CLASS, METHOD, "  cartItem.partNumber  " + cartItem.partNumber);
        IBEUtil.log(CLASS, METHOD, "  cartItem.uomName  " + cartItem.uomName);
      }
    } // end if child item and loadControlRec.fillChildItems says to load this
    else {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  NO ITEM.LOAD ");
      }
    }
    cartItem.cartLineId =
      shopCart.lineRec[lineIndex].quote_line_id.toString();

    if (!cartItem.isOrderable()) shopCart.containsUnorderableItems = true;

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  cartItem.cartLineId  " + cartItem.cartLineId);
      IBEUtil.log(CLASS, METHOD, "  cartItem.isOrderable " + cartItem.isOrderable);
      IBEUtil.log(CLASS, METHOD, "  shopCart.containsUnorderableItems " + shopCart.containsUnorderableItems);
    }
    cartItem.cartId = shopCart.cartId;
    cartItem.customerName = shopCart.customerName;
    cartItem.inventoryItemId =
      shopCart.lineRec[lineIndex].inventory_item_id.toString();
    cartItem.uom = shopCart.lineRec[lineIndex].uom_code.toString();
    cartItem.organizationId =
      shopCart.lineRec[lineIndex].organization_id.toString();
    cartItem.itemType = shopCart.lineRec[lineIndex].item_type_code;
    cartItem.categoryCode =
      IBEUtil.nonNull(shopCart.lineRec[lineIndex].line_category_code);
    cartItem.orderTypeId =
      IBEUtil.toString(shopCart.lineRec[lineIndex].order_line_type_id);
    cartItem.parentItemId = parentItemId;

/****************** if set to do so, find the PRG qualifying item ids  **********/
    if(shopCart.loadControlRec.loadPRGItems && EMPTY_STRING.equals(parentItemId))
    {
      if(shopCart.logEnabled) {
        IBEUtil.log(CLASS, METHOD, "  asked to check on PRG item");
      }

      if ("F".equals(shopCart.lineRec[lineIndex].pricing_line_type_indicator))
      {
        if(shopCart.logEnabled) {
          IBEUtil.log(CLASS, METHOD, "  found PRG item!!");
        }
        cartItem.setPricingLineTypeIndicator(shopCart.lineRec[lineIndex].pricing_line_type_indicator);
        if (!shopCart.containsPRGItems)
        { // this is only happen on the 1st PRG item
          if(shopCart.logEnabled) {
            IBEUtil.log(CLASS, METHOD, "  calling utility api to get PRG qualifying item ids!!");
          }
          shopCart.qualifyingItemIdsForPRG = ShoppingCartUtil.getPRGQualItemIds(shopCart.cartId);
        }
        shopCart.containsPRGItems = true;
        
        if (shopCart.qualifyingItemIdsForPRG != null)
        {
          if(shopCart.logEnabled) {
            IBEUtil.log(CLASS, METHOD, "  trying to get array list for lineid: " + shopCart.lineRec[lineIndex].quote_line_id);
          }
          ArrayList qualifyingItemIds = (ArrayList) shopCart.qualifyingItemIdsForPRG.get(shopCart.lineRec[lineIndex].quote_line_id);
          if(shopCart.logEnabled) {
            IBEUtil.log(CLASS, METHOD, "  got an array list for lineid: " + shopCart.lineRec[lineIndex].quote_line_id);
          }
          if (qualifyingItemIds != null)
          {
          // if we get a list of item id's, populate the PRG item's array
            int numItems = qualifyingItemIds.size();
            if (numItems > 0)
            {
              cartItem.qualifyingItemIds = new String[numItems];
              for (int i = 0; i < numItems; i++)
              {
                BigDecimal itemId = (BigDecimal) qualifyingItemIds.get(i);
                cartItem.qualifyingItemIds[i] = itemId.toString();
                if(shopCart.logEnabled) {
                 IBEUtil.log(CLASS, METHOD, "  qualifying item id[" + i + "]: " + itemId.toString());
                }
              }
            }
          } // end if we have an array list of qualifying item ids.
        } // end if we have the hash map
      } // end if F
    } // end loadPRGItems

/*****DFF*****/
       if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  loading FF info: " + shopCart.lineRec[lineIndex].attribute_category);
       }
       cartItem.attributeCategory = shopCart.lineRec[lineIndex].attribute_category;
       cartItem.attribute1 = shopCart.lineRec[lineIndex].attribute1;
       cartItem.attribute2 = shopCart.lineRec[lineIndex].attribute2;
       cartItem.attribute3 = shopCart.lineRec[lineIndex].attribute3;
       cartItem.attribute4 = shopCart.lineRec[lineIndex].attribute4;
       cartItem.attribute5 = shopCart.lineRec[lineIndex].attribute5;
       cartItem.attribute6 = shopCart.lineRec[lineIndex].attribute6;
       cartItem.attribute7 = shopCart.lineRec[lineIndex].attribute7;
       cartItem.attribute8 = shopCart.lineRec[lineIndex].attribute8;
       cartItem.attribute9 = shopCart.lineRec[lineIndex].attribute9;
       cartItem.attribute10 = shopCart.lineRec[lineIndex].attribute10;
       cartItem.attribute11 = shopCart.lineRec[lineIndex].attribute11;
       cartItem.attribute12 = shopCart.lineRec[lineIndex].attribute12;
       cartItem.attribute13 = shopCart.lineRec[lineIndex].attribute13;
       cartItem.attribute14 = shopCart.lineRec[lineIndex].attribute14;
       cartItem.attribute15 = shopCart.lineRec[lineIndex].attribute15;

/*****DFF****/

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  cartItem.uom  " + cartItem.uom);
      IBEUtil.log(CLASS, METHOD, "  cartItem.organizationId " + cartItem.organizationId);
      IBEUtil.log(CLASS, METHOD, "  cartItem.itemType  " + cartItem.itemType);
      IBEUtil.log(CLASS, METHOD, "  cartItem.AttrCateg  " + cartItem.attributeCategory);
    }
    // so that the fillAllLinesAgreementInfo can map the Agreement objects to the ShopCartItems
    if (shopCart.loadControlRec.lineAgreements) {
      cartItem.agreementId =
        IBEUtil.toString(shopCart.lineRec[lineIndex].agreement_id);
    }
    // so that the fillAllLinesCommitmentInfo can map the Commitment objects to the ShopCartItems
    if (shopCart.loadControlRec.lineCommitments) {
      cartItem.commitmentId =
        IBEUtil.toString(shopCart.lineRec[lineIndex].commitment_id);
    }
    if(shopCart.lineRec[lineIndex].start_date_active != null)
    {
      cartItem.startDateActive =
        ShoppingCartUtil.formatTimestamp(shopCart.lineRec[lineIndex].start_date_active);
    }

    String  currencyCode;
    if((shopCart.lineRec[lineIndex].currency_code == null)
            || ((shopCart.lineRec[lineIndex].currency_code).trim().length()
                == 0))
    {

      // use cart-level currency_code
      currencyCode = shopCart.headerRec.currency_code;
    }
    else
    {
      currencyCode = shopCart.lineRec[lineIndex].currency_code;
    }
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " currencyCode is " + currencyCode);

}
    cartItem.quantity = numFormat.formatNumber(shopCart.lineRec[lineIndex].quantity); //bug#4469168
    if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, " cartItem quantity is " + cartItem.quantity);
    if(!isCfgItem)
    {
      // if we're not set to showPrice, then don't set or calculate anything
      if((shopCart.lineRec[lineIndex].line_list_price == null) || !shopCart.loadControlRec.showPrice)
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  NO FILLING PRICE: line_list_price is null or showPrice is false ");
}
        cartItem.unitListPrice = cartItem.totalListPrice = EMPTY_STRING;
        cartItem.unitDiscount = cartItem.totalDiscount = EMPTY_STRING;
        cartItem.unitNetPrice = cartItem.totalNetPrice = EMPTY_STRING;
      }
      else
      {
        // quantity is not null
        double  quantityDouble =
          shopCart.lineRec[lineIndex].quantity.doubleValue();
        double  unitPriceDouble =
          shopCart.lineRec[lineIndex].line_list_price.doubleValue();
        double  lineTotalDouble = unitPriceDouble * quantityDouble;

        // only set these values if we are going to show net prices
        double  unitDiscDouble = 0;
        double  unitNetDouble = 0;
        double  lineNetTotalDouble = 0;
        double  lineDiscTotalDouble = 0;

        if (shopCart.loadControlRec.formatNetPrices) {
          unitDiscDouble = shopCart.lineRec[lineIndex].line_adjusted_amount.doubleValue();
          unitNetDouble = shopCart.lineRec[lineIndex].line_quote_price.doubleValue();
          lineNetTotalDouble = unitNetDouble * quantityDouble;
          lineDiscTotalDouble = unitDiscDouble * quantityDouble;
        }

        if (shopCart.loadControlRec.loadItems &&
            (EMPTY_STRING.equals(parentItemId) || isSvcItem ||
            (!EMPTY_STRING.equals(parentItemId) && shopCart.loadControlRec.fillChildItems))) {
          cartItem.unitListPrice = PriceObject.formatNumber(currencyCode,
                  unitPriceDouble);
          cartItem.totalListPrice = PriceObject.formatNumber(currencyCode,
                  lineTotalDouble);

          if (shopCart.loadControlRec.formatNetPrices) {
            cartItem.unitDiscount = PriceObject.formatNumber(currencyCode,
                  unitDiscDouble);
            cartItem.unitNetPrice = PriceObject.formatNumber(currencyCode,
                  unitNetDouble);
            cartItem.totalDiscount = PriceObject.formatNumber(currencyCode,
                  lineDiscTotalDouble);
            cartItem.totalNetPrice = PriceObject.formatNumber(currencyCode,
                  lineNetTotalDouble);
          } else {
            if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  NO FORMATTING NET PRICES ");
}
          }

        } // end if child item & set to fillChildItems
        else {
          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "  NO FORMATTING PRICE ");
}
        }
      } // end of if have price & set to showPrice
    } // end of ! isCfgItem
    else
    {
      double lineQty = shopCart.lineRec[lineIndex].quantity.doubleValue();
      cartItem.cfgQuantity = numFormat.formatNumber(new BigDecimal(lineQty/modelQuantity));
      if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, " cartItem.cfgQuantity is " + cartItem.cfgQuantity);
      // if we're not set to showPrice, then don't set or calculate anything
      if ((shopCart.lineRec[lineIndex].line_list_price == null) || !shopCart.loadControlRec.showPrice)
      {
        if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, "  NO FILLING PRICE: line_list_price is null or showPrice is false ");
        String  zeroNumber = PriceObject.formatNumber(currencyCode, 0);
        cartItem.unitListPrice = zeroNumber;
        cartItem.unitDiscount = zeroNumber;
        cartItem.unitNetPrice = zeroNumber;
        cartItem.totalListPrice = zeroNumber;
        cartItem.totalDiscount = zeroNumber;
        cartItem.totalNetPrice = zeroNumber;
      }
      else
      {
        double  unitListDouble = shopCart.lineRec[lineIndex].line_list_price.doubleValue();
        double  totalListDouble = unitListDouble * lineQty;

        // only set these values if we are going to show net prices
        double  unitDiscountDouble = 0;
        double  unitNetDouble = 0;
        double  totalDiscountDouble = 0;
        double  totalNetDouble = 0;

        if (shopCart.loadControlRec.formatNetPrices) 
        {
          unitDiscountDouble = shopCart.lineRec[lineIndex].line_adjusted_amount.doubleValue();
          unitNetDouble = shopCart.lineRec[lineIndex].line_quote_price.doubleValue();
          totalDiscountDouble = unitDiscountDouble * lineQty;
          totalNetDouble = unitNetDouble * lineQty;
        }

        if (shopCart.loadControlRec.loadItems && (EMPTY_STRING.equals(parentItemId) ||
           (!EMPTY_STRING.equals(parentItemId) && shopCart.loadControlRec.fillChildItems))) 
        {
          cartItem.unitListPrice = PriceObject.formatNumber(currencyCode, unitListDouble);
          cartItem.totalListPrice = PriceObject.formatNumber(currencyCode, totalListDouble);
          if (shopCart.loadControlRec.formatNetPrices) 
          {
            cartItem.unitDiscount = PriceObject.formatNumber(currencyCode, unitDiscountDouble);
            cartItem.unitNetPrice = PriceObject.formatNumber(currencyCode, unitNetDouble);
            cartItem.totalDiscount = PriceObject.formatNumber(currencyCode, totalDiscountDouble);
            cartItem.totalNetPrice = PriceObject.formatNumber(currencyCode, totalNetDouble);
          } 
          else if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, "  NO FORMATTING NET PRICES ");
        } // end if child item & set to fillChildItems
        else if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, "  NO FORMATTING PRICE ");

        dUnitListPrice[0] += totalListDouble/modelQuantity;
        dTotalListPrice[0] += totalListDouble;
        if (shopCart.loadControlRec.formatNetPrices)
        {
          dUnitDiscount[0] += totalDiscountDouble/modelQuantity;
          dUnitNetPrice[0] += totalNetDouble/modelQuantity;
          dTotalDiscount[0] += totalDiscountDouble;
          dTotalNetPrice[0] += totalNetDouble;
        } 
        else if(shopCart.logEnabled) IBEUtil.log(CLASS, METHOD, "  NOT SUMMING UP NET PRICES ");
      } // end if have price & set to showPrice
    }

    if(shopCart.lineDetRec != null)
    {
      for(int detLIndx = 0; detLIndx < shopCart.lineDetRec.length; detLIndx++)
      {
        if(!((shopCart.lineDetRec[detLIndx].quote_line_id).equals(shopCart.lineRec[lineIndex].quote_line_id)))
        {
          continue;
        }

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, " line detail rcrd found ");
}
        cartItem.detailRecordId =
          shopCart.lineDetRec[detLIndx].quote_line_detail_id.toString();

        // populate the config header id and config rev number
        if(isMdlItem)
        {
          cartItem.configHeaderId =
            (shopCart.lineDetRec[detLIndx].config_header_id).toString();
          cartItem.configRevisionNumber =
            (shopCart.lineDetRec[detLIndx].config_revision_num).toString();
          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "cartItem.configHeaderId = " +
            cartItem.configHeaderId);
          IBEUtil.log(CLASS, METHOD, "cartItem.configRevisionNumber = " +
            cartItem.configRevisionNumber);
}
          cartItem.completeConfigurationFlag =
            (YES.equals(shopCart.lineDetRec[detLIndx].complete_configuration_flag))
            ? true : false;
          cartItem.validConfigurationFlag =
            (YES.equals(shopCart.lineDetRec[detLIndx].valid_configuration_flag))
            ? true : false;

          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "cartItem.completeConfigurationFlag = " +
            cartItem.completeConfigurationFlag);
          IBEUtil.log(CLASS, METHOD, "cartItem.validConfigurationFlag = " +
            cartItem.validConfigurationFlag);
}
          if((shopCart.isOrderable)
                  && ((!cartItem.completeConfigurationFlag)
                      || (!cartItem.validConfigurationFlag)))
          {
            shopCart.isOrderable = false;
            if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCart.isOrderable = " +
              shopCart.isOrderable);
}
          }
        }

        if(isSvcItem)
        {
          ServiceItem svcItem = (ServiceItem) cartItem;

          svcItem.serviceReferenceType =
            IBEUtil.nonNull(shopCart.lineDetRec[detLIndx].service_ref_type_code);
          svcItem.servicePeriod =
            IBEUtil.nonNull(shopCart.lineDetRec[detLIndx].service_period);
          svcItem.serviceDuration =
            IBEUtil.toString(shopCart.lineDetRec[detLIndx].service_duration);

          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "svcItem.serviceReferenceType = " +
            svcItem.serviceReferenceType);
          IBEUtil.log(CLASS, METHOD, "svcItem.servicePeriod = " +
            svcItem.servicePeriod);
         IBEUtil.log(CLASS, METHOD, "svcItem.serviceDuration = " +
            svcItem.serviceDuration);

}
        }

        break;
      }
    }

    if (shopCart.loadControlRec.lineShipping || shopCart.loadControlRec.lineBilling) {
      // common code to prepare for either lineShipping or lineBilling
      cartItem.soldtoContactName = shopCart.soldtoContactName;
      cartItem.soldtoContactPhone = shopCart.soldtoContactPhone;
      cartItem.soldtoContactEmail = shopCart.soldtoContactEmail;
      if(shopCart.headerRec.cust_account_id != null)
      {
        cartItem.customerAccountId =
          shopCart.headerRec.cust_account_id.toString();
      }

    }

    if(shopCart.loadControlRec.lineShipping)
    {
      fillOneLineShippingDetails(shopCart, cartItem, lineIndex);
    }

    if(shopCart.loadControlRec.lineBilling)
    {
      fillOneLineBillingDetails(shopCart, cartItem, lineIndex);
    }

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }



  /**
   * NEW METHOD
   */
  private static void fillOneLineBillingDetails(ShoppingCart shopCart,
                                              ShoppingCartItem shopCartItem,
                                              int lineIndex) throws FrameworkException,
                                              SQLException
  {
    String METHOD = "fillOneLineBillingDetails";
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    int i = lineIndex;
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "i is = " + i);

}
    if((shopCart.lineRec[i].invoice_to_party_site_id != null)
            && (!shopCart.lineRec[i].invoice_to_party_site_id.equals(gMissNum)))
    {
      shopCartItem.billtoPartySiteId =
        shopCart.lineRec[i].invoice_to_party_site_id.toString();
    }
    else
    {
      shopCartItem.billtoPartySiteId = shopCart.billtoPartySiteId;
    }
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoPartySiteId is " +
      shopCartItem.billtoPartySiteId);

}
/******************* BILL TO CUST ACCOUNT (CUSTOMER) ******************************/
    if((shopCart.lineRec[i].invoice_to_cust_account_id != null)
            && (!shopCart.lineRec[i].invoice_to_cust_account_id.equals(gMissNum)))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level ship to cust account id exists");
}
      shopCartItem.billtoCustomerAccountId =
        shopCart.lineRec[i].invoice_to_cust_account_id.toString();
      String[]  idNameType =
        ShoppingCartUtil.getIdNameTypeofAccount(shopCartItem.billtoCustomerAccountId);

      shopCartItem.billtoCustomerPartyType = idNameType[2].toLowerCase();
      shopCartItem.billtoCustomerName = idNameType[1];
      shopCartItem.billtoCustomerPartyId = idNameType[0];

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCartItem.billtoCustomerPartyType = " + shopCartItem.billtoCustomerPartyType);
      IBEUtil.log(CLASS, METHOD, "shopCartItem.billtoCustomerPartyId = " + shopCartItem.billtoCustomerPartyId);
     IBEUtil.log(CLASS, METHOD, "shopCartItem.billtoCustomerName = " + shopCartItem.billtoCustomerName);
}
    }
    else
    {
      shopCartItem.billtoCustomerAccountId = shopCart.billtoCustomerAccountId;
      shopCartItem.billtoCustomerPartyType = shopCart.billtoCustomerPartyType;
      shopCartItem.billtoCustomerPartyId = shopCart.billtoCustomerPartyId;
      shopCartItem.billtoCustomerName = shopCart.billtoCustomerName;
    }

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoCustomerAccountId is " +
      shopCartItem.billtoCustomerAccountId);
    IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoCustomerPartyType is " +
      shopCartItem.billtoCustomerPartyType);
   IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoCustomerName is " +
      shopCartItem.billtoCustomerName);
    IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoCustomerPartyId is " +
      shopCartItem.billtoCustomerPartyId);
}
/*********************** BILL TO PARTY SITE (ADDRESS) ******************************/
    if((shopCart.lineRec[i].invoice_to_party_site_id != null)
            && (!shopCart.lineRec[i].invoice_to_party_site_id.equals(gMissNum)))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level ship to party site id exists");
}
      shopCartItem.billtoPartySiteId =
        shopCart.lineRec[i].invoice_to_party_site_id.toString();

      /*
       * if the quote has a ship to address, then we need to determine the
       * onwer of the address. If the owner is the same as the customer,
       * then set the party site type to customer, else set it to contact
       */

      oracle.apps.ibe.tcav2.Address addr = null;
      addr                               = AddressManager.getAddress(new BigDecimal(shopCartItem.billtoPartySiteId));

      if(addr.getPartyId().equals(shopCartItem.billtoCustomerPartyId))
      {
        shopCartItem.billtoPartySiteType = "customer";
      }
      else
      {
        shopCartItem.billtoPartySiteType = "contact";
      }
    }
    else if (shopCartItem.billtoCustomerAccountId.equals(shopCart.billtoCustomerAccountId)) 
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Def addrs from header");
}
      shopCartItem.billtoPartySiteId = shopCart.billtoPartySiteId;
      shopCartItem.billtoPartySiteType = shopCart.billtoPartySiteType;
    }

    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoPartySiteId is " +
      shopCartItem.billtoPartySiteId);
    IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoPartySiteType is " +
      shopCartItem.billtoPartySiteType);
}
/******************* BILL TO PARTY (CONTACT) ******************************/
    if((shopCart.lineRec[i].invoice_to_party_id != null)
            && (!shopCart.lineRec[i].invoice_to_party_id.equals(gMissNum)))
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level bill to party id exists");
}
      shopCartItem.billtoContactPartyId =
        shopCart.lineRec[i].invoice_to_party_id.toString();
      if (shopCartItem.billtoContactPartyId.equals(shopCart.billtoContactPartyId)) {
        shopCartItem.billtoContactName = shopCart.billtoContactName;
      } else {
        shopCartItem.billtoContactName = ContactManager.get(new BigDecimal(shopCartItem.billtoContactPartyId)).getPerson().getPartyName();
      }

      String[]  emailAndPhone =
        getPrimaryEmailAndPhone(shopCartItem.billtoContactPartyId);

      shopCartItem.billtoContactEmail = IBEUtil.nonNull(emailAndPhone[3]);
      shopCartItem.billtoContactPhone = IBEUtil.nonNull(emailAndPhone[0]);
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoContactPartyId is " +
        shopCartItem.billtoContactPartyId);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoContactName is " +
        shopCartItem.billtoContactName);
}
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoContactEmail is " +
        shopCartItem.billtoContactEmail);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.billtoContactPhone is " +
        shopCartItem.billtoContactPhone);
}
    }
/*
    else
    {
      shopCartItem.billtoContactPartyId = shopCart.billtoContactPartyId;
      shopCartItem.billtoContactName = shopCart.billtoContactName;
      shopCartItem.billtoContactPhone = shopCart.billtoContactPhone;
      shopCartItem.billtoContactEmail = shopCart.billtoContactEmail;
    }
*/  
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }


  private static void fillOneLineShippingDetails(ShoppingCart shopCart,
                                              ShoppingCartItem shopCartItem,
                                              int lineIndex) throws FrameworkException,
                                              SQLException
  {
    String METHOD = "fillOneLineShippingDetails";
    /*
     * for every shoppingcart item we have to load the following:
     * shipto customer name, contact name, partySiteId, party type and party site type
     */
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    int i = lineIndex;
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "i is = " + i);
}
    if(shopCart.lineShipmentRec[i] == null)
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line shipment record does not exist");
}
      shopCart.lineShipmentRec[i] = new ShipmentRecord();
    }

    ShipmentRecord  lineShipRecord = shopCart.lineShipmentRec[i];
/*   (moved to common area)
    shopCartItem.soldtoContactName = shopCart.soldtoContactName;
    shopCartItem.soldtoContactPhone = shopCart.soldtoContactPhone;
    shopCartItem.soldtoContactEmail = shopCart.soldtoContactEmail;
*/
    if(lineShipRecord != null)
    {
      if((lineShipRecord.shipping_instructions != null)
              && (!lineShipRecord.shipping_instructions.equals(gMissChar)))
      {
        shopCartItem.shippingInstructions =
          lineShipRecord.shipping_instructions;
      }
      else
      {
        shopCartItem.shippingInstructions = shopCart.shippingInstructions;
      }

      if((lineShipRecord.packing_instructions != null)
              && (!lineShipRecord.packing_instructions.equals(gMissChar)))
      {
        shopCartItem.packingInstructions =
          lineShipRecord.packing_instructions;
      }
      else
      {
        shopCartItem.packingInstructions = shopCart.packingInstructions;
      }
/* moved to common area
      if(shopCart.headerRec.cust_account_id != null)
      {
        shopCartItem.customerAccountId =
          shopCart.headerRec.cust_account_id.toString();
      }
*/
      if((lineShipRecord.shipment_id != null)
              && (!lineShipRecord.shipment_id.equals(gMissNum)))
      {
        shopCartItem.shipmentId = lineShipRecord.shipment_id.toString();
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line Shipment Id is " + shopCartItem.shipmentId);
}
      }

      if((lineShipRecord.ship_method_code != null)
              && (!lineShipRecord.ship_method_code.equals(gMissChar)))
      {
        shopCartItem.shippingMethod = lineShipRecord.ship_method_code;

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"ABt to call QuoteUtil.getAllShippingMethods");
}
        LookupObject[]  los = QuoteUtil.getAllShippingMethods();
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"Done QuoteUtil.getAllShippingMethods");

}
        if(los != null)
        {
          if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"Ship methods exist");
}
          int losLength = los.length;

          for(int k = 0; k < losLength; k++)
          {
            if(los[k].getLookupCode().equals(shopCartItem.shippingMethod))
            {
              shopCartItem.shippingMethodDescription = los[k].getMeaning();
            }
          }
        }
      }
      else
      {
        shopCartItem.shippingMethod = shopCart.shippingMethod;
        shopCartItem.shippingMethodDescription =
          shopCart.shippingMethodDescription;
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,
          "Line Shipping method is " + shopCartItem.shippingMethodDescription);

}
      }

      if((lineShipRecord.request_date != null)
              && (!lineShipRecord.request_date.equals(gMissDate)))
      {
        shopCartItem.requestedDeliveryDate =
          ShoppingCartUtil.formatTimestamp(lineShipRecord.request_date);
      }
      else
      {
        shopCartItem.requestedDeliveryDate = shopCart.requestedDeliveryDate;
      }

/******************* SHIP TO CUST ACCOUNT (CUSTOMER) ******************************/
      if((lineShipRecord.ship_to_cust_account_id != null)
              && (!lineShipRecord.ship_to_cust_account_id.equals(gMissNum)))
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level ship to cust account id exists");
}
        shopCartItem.shiptoCustomerAccountId =
          lineShipRecord.ship_to_cust_account_id.toString();
        shopCartItem.shiptoCustomerName = lineShipRecord.ship_to_cust_name;

        String[]  idNameType =
          ShoppingCartUtil.getIdNameTypeofAccount(shopCartItem.shiptoCustomerAccountId);

        shopCartItem.shiptoCustomerPartyType = idNameType[2].toLowerCase();
        shopCartItem.shiptoCustomerPartyId = idNameType[0];

        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCartItem.shiptoCustomerPartyType = " + shopCartItem.shiptoCustomerPartyType);
        IBEUtil.log(CLASS, METHOD, "shopCartItem.shiptoCustomerPartyId = " + shopCartItem.shiptoCustomerPartyId);
        IBEUtil.log(CLASS, METHOD, "shopCartItem.shiptoCustomerName = " + shopCartItem.shiptoCustomerName);
            }
      }
      else
      {
        shopCartItem.shiptoCustomerAccountId =
          shopCart.shiptoCustomerAccountId;

        /*
         * shopCartItem.shiptoContactName = shopCart.shiptoContactName;
         * shopCartItem.shiptoContactPhone = shopCart.shiptoContactPhone;
         * shopCartItem.shiptoContactEmail = shopCart.shiptoContactEmail;
         */

        shopCartItem.shiptoCustomerPartyType =
          shopCart.shiptoCustomerPartyType;
        shopCartItem.shiptoCustomerPartyId = shopCart.shiptoCustomerPartyId;
        shopCartItem.shiptoCustomerName = shopCart.shiptoCustomerName;
      }

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoCustomerAccountId is " +
        shopCartItem.shiptoCustomerAccountId);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoCustomerPartyType is " +
        shopCartItem.shiptoCustomerPartyType);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoCustomerName is " +
        shopCartItem.shiptoCustomerName);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoCustomerPartyId is " +
        shopCartItem.shiptoCustomerPartyId);
   }
/*********************** SHIP TO PARTY SITE (ADDRESS) ******************************/
      if((lineShipRecord.ship_to_party_site_id != null)
              && (!lineShipRecord.ship_to_party_site_id.equals(gMissNum)))
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level ship to party site id exists");
}
        shopCartItem.shiptoPartySiteId =
          lineShipRecord.ship_to_party_site_id.toString();

        /*
         * if the quote has a ship to address, then we need to determine the
         * onwer of the address. If the owner is the same as the customer,
         * then set the party site type to customer, else set it to contact
         */
        oracle.apps.ibe.tcav2.Address addr = null;
        addr                               = AddressManager.getAddress(new BigDecimal(shopCartItem.shiptoPartySiteId));

        if(addr.getPartyId().equals(shopCartItem.shiptoCustomerPartyId))
        {
          shopCartItem.shiptoPartySiteType = "customer";
        }
        else
        {
          shopCartItem.shiptoPartySiteType = "contact";
        }

      }
      else if (shopCartItem.shiptoCustomerAccountId.equals(shopCart.shiptoCustomerAccountId))
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Def addrs from header");
}
        shopCartItem.shiptoPartySiteId = shopCart.shiptoPartySiteId;
        shopCartItem.shiptoPartySiteType = shopCart.shiptoPartySiteType;
      }

      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoPartySiteId is " +
        shopCartItem.shiptoPartySiteId);
      IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoPartySiteType is " +
        shopCartItem.shiptoPartySiteType);
}
/******************* SHIP TO PARTY (CONTACT) ******************************/
      if((lineShipRecord.ship_to_party_id != null)
              && (!lineShipRecord.ship_to_party_id.equals(gMissNum)))
      {
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Line level ship to party id exists");
}
        shopCartItem.shiptoContactPartyId =
          lineShipRecord.ship_to_party_id.toString();
        shopCartItem.shiptoContactName =
          IBEUtil.nonNull(lineShipRecord.ship_to_contact_first_name) + " "
          + IBEUtil.nonNull(lineShipRecord.ship_to_contact_last_name);

        String[]  emailAndPhone =
          getPrimaryEmailAndPhone(shopCartItem.shiptoContactPartyId);

        shopCartItem.shiptoContactEmail = IBEUtil.nonNull(emailAndPhone[3]);
        shopCartItem.shiptoContactPhone = IBEUtil.nonNull(emailAndPhone[0]);
        if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoContactPartyId is " +
          shopCartItem.shiptoContactPartyId);
        IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoContactName is " +
          shopCartItem.shiptoContactName);
        IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoContactEmail is " +
          shopCartItem.shiptoContactEmail);
        IBEUtil.log(CLASS, METHOD,"shopCartItem.shiptoContactPhone is " +
          shopCartItem.shiptoContactPhone);
}
      }

      /*
       * else
       * {
       * shopCartItem.shiptoContactPartyId = shopCart.shiptoContactPartyId;
       * shopCartItem.shiptoContactName = shopCart.shiptoContactName;
       * shopCartItem.shiptoContactPhone = shopCart.shiptoContactPhone;
       * shopCartItem.shiptoContactEmail = shopCart.shiptoContactEmail;
       * }
       */

    }
    else
    {
      if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD, "Defaulting all values from header!");
}
      shopCartItem.shippingMethod = shopCart.shippingMethod;
      shopCartItem.packingInstructions = shopCart.packingInstructions;
      shopCartItem.shippingInstructions = shopCart.shippingInstructions;
      shopCartItem.requestedDeliveryDate = shopCart.requestedDeliveryDate;
      shopCartItem.shiptoCustomerAccountId = shopCart.shiptoCustomerAccountId;
      shopCartItem.shiptoContactName = shopCart.shiptoContactName;
      shopCartItem.shiptoContactPhone = shopCart.shiptoContactPhone;
      shopCartItem.shiptoContactEmail = shopCart.shiptoContactEmail;
      shopCartItem.shiptoCustomerPartyType = shopCart.shiptoCustomerPartyType;
      shopCartItem.shiptoCustomerPartyId = shopCart.shiptoCustomerPartyId;
      shopCartItem.shiptoCustomerName = shopCart.shiptoCustomerName;
      shopCartItem.shiptoContactPartyId = shopCart.shiptoContactPartyId;
      shopCartItem.shiptoContactName = shopCart.shiptoContactName;
      shopCartItem.shiptoContactPhone = shopCart.shiptoContactPhone;
      shopCartItem.shiptoContactEmail = shopCart.shiptoContactEmail;
      shopCartItem.shiptoPartySiteId = shopCart.shiptoPartySiteId;
      shopCartItem.shiptoPartySiteType = shopCart.shiptoPartySiteType;

      // default all values from header
    }
    if(shopCart.logEnabled) { IBEUtil.log(CLASS, METHOD,"DONE");
}
  }

  /**
   * Method declaration
   * 
   * 
   * @param soldtoCustAccountId
   *
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupLineShipmentRecords(String soldtoCustAccountId)
          throws ShoppingCartException
  {

    // for each cart item, populate the foll in the line ship record
    // the ship to cust accnt, ship to party id, ship to party site
    // based on tca defaulting
    // also set the shipping method, shipping instr, packing instr, req delv date
    // line shipment id, quote line id, quote_header_id, operation_code
    String  METHOD = "setupLineShipmentRecords";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;

      if(numCartItems > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems = " + numCartItems);

}
        if(lineShipmentRec == null)
        {
          lineShipmentRec = new ShipmentRecord[numCartItems];
        }

        BigDecimal  shoppingCartId = new BigDecimal(cartId);

        for(int i = 0; i < numCartItems; i++)
        {
          if(lineShipmentRec[i] == null)
          {
            lineShipmentRec[i] = new ShipmentRecord();
          }

          lineShipmentRec[i].operation_code = UPDATE_OPCODE;
          lineShipmentRec[i].quote_line_id =
            new BigDecimal(shopCartItems[i].cartLineId);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "quote_line_id = " + shopCartItems[i].cartLineId);

}
          setupShipmentRecord(lineShipmentRec[i], shoppingCartId,
                              soldtoCustAccountId,
                              shopCartItems[i].shiptoCustomerAccountId,
                              shopCartItems[i].shiptoContactPartyId,
                              shopCartItems[i].shiptoPartySiteId,
                              shopCartItems[i].shiptoPartySiteType,
                              shopCartItems[i].shipmentId,
                              shopCartItems[i].requestedDeliveryDate,
                              shopCartItems[i].shippingMethod,
                              shopCartItems[i].shippingInstructions,
                              shopCartItems[i].packingInstructions, false);
          shopCartItems[i].logInputVariables(METHOD);
        }
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                    "number of items in cart are ZERO !!, nothing to save");
}
      }
    }
    else
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "method was called, but items array is NULL !!");
}
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   *
   * @param shipmentRecord
   * @param shoppingCartId
   * @param soldtoCustomerAccountId
   * @param shiptoCustomerAccountId
   * @param shiptoContactPartyId
   * @param shiptoPartySiteId
   * @param shiptoPartySiteType
   * @param shipmentId
   * @param requestedDeliveryDate
   * @param shippingMethod
   * @param shippingInstructions
   * @param packingInstructions
   *
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupShipmentRecord(ShipmentRecord shipmentRecord,
                                   BigDecimal shoppingCartId,
                                   String soldtoCustomerAccountId,
                                   String shiptoCustomerAccountId,
                                   String shiptoContactPartyId,
                                   String shiptoPartySiteId,
                                   String shiptoPartySiteType,
                                   String shipmentId,
                                   String requestedDeliveryDate,
                                   String shippingMethod,
                                   String shippingInstructions,
                                   String packingInstructions,
                                   boolean saveHeader) throws ShoppingCartException
  {
    String  METHOD = "setupShipmentRecord";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "soldtoCustomerAccountId = " + soldtoCustomerAccountId);
   IBEUtil.log(CLASS, METHOD, "shiptoCustomerAccountId = " + shiptoCustomerAccountId);
    IBEUtil.log(CLASS, METHOD, "shiptoContactPartyId = " + shiptoContactPartyId);
    IBEUtil.log(CLASS, METHOD, "shiptoPartySiteId = " + shiptoPartySiteId);
    IBEUtil.log(CLASS, METHOD, "shiptoPartySiteType = " + shiptoPartySiteType);
   IBEUtil.log(CLASS, METHOD, "shipmentId = " + shipmentId);
    IBEUtil.log(CLASS, METHOD, "shoppingCartId = " + shoppingCartId);
}
    try
    {
      shipmentRecord.quote_header_id = shoppingCartId;

      if((requestedDeliveryDate != null)
              && (!requestedDeliveryDate.equals(EMPTY_STRING)))
      {
        Timestamp reqDelvTS =
          ShoppingCartUtil.checkTimeStamp(requestedDeliveryDate);

        if(reqDelvTS != null)
        {
          shipmentRecord.request_date = reqDelvTS;
        }
        else
        {
          throw new ShoppingCartException("IBE_SC_DELV_DATE_PAST");
        }
      }
      else
      {
        shipmentRecord.request_date = null;
      }

      shipmentRecord.shipping_instructions = shippingInstructions;
      shipmentRecord.packing_instructions = packingInstructions;
      shipmentRecord.ship_method_code = shippingMethod;

      if((shipmentId != null) && (!shipmentId.equals("")))
      {
        shipmentRecord.shipment_id = new BigDecimal(shipmentId);
        shipmentRecord.operation_code = UPDATE_OPCODE;
      }
      else
      {
        shipmentRecord.operation_code = CREATE_OPCODE;
      }

      if((shiptoPartySiteId != null) &&!shiptoPartySiteId.equals(""))
      {
        shipmentRecord.ship_to_party_site_id =
          new BigDecimal(shiptoPartySiteId);
      }
      else if((shiptoContactPartyId != null)
              && (shiptoCustomerAccountId != null))
      {
        throw new ShoppingCartException("IBE_SC_NO_SHIP_ADDRS");
      }

      if((shiptoContactPartyId != null) && (!shiptoContactPartyId.equals("")))
      {
        shipmentRecord.ship_to_party_id =
          new BigDecimal(shiptoContactPartyId);
      }
      else if(shiptoContactPartyId == null)
      {
        shipmentRecord.ship_to_party_id = null;

        if((shiptoPartySiteType != null) && (!shiptoPartySiteType.equals(""))
                && (shiptoPartySiteType.equals("contact")))
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "nulling ship_to_party_site_id cos the addrs belongs to the contact");

}
          shipmentRecord.ship_to_party_site_id = null;
        }
      }

      if ((shiptoCustomerAccountId != null)
              &&!("".equals(shiptoCustomerAccountId)))
      {

        if ( ( (saveHeader)  && (!shiptoCustomerAccountId.equals(soldtoCustomerAccountId)) )
            || (!saveHeader) )
        {
            shipmentRecord.ship_to_cust_account_id =
              new BigDecimal(shiptoCustomerAccountId);
        } else if ((saveHeader)  && (shiptoCustomerAccountId.equals(soldtoCustomerAccountId))) {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "nulling ship_to_cust_account_id");
}
          shipmentRecord.ship_to_cust_account_id = null;
        }
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "nulling ship_to_cust_account_id");

}
        shipmentRecord.ship_to_cust_account_id = null;

        if(shiptoCustomerAccountId == null)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
            "nulling ship_to_party_site_id and ship_to_party_id");

}
          shipmentRecord.ship_to_party_site_id = null;
          shipmentRecord.ship_to_party_id = null;
        }
      }
    }
    catch(ParseException e)
    {
      throw new ShoppingCartException("IBE_INVALID_REQ_DELV_DATE");
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "Exception occured while constructing BigDecimal: "
                  + e.getMessage());
      IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "Exception occured while constructing BigDecimal: "
                  + e.getMessage());
      IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method to place an order.  Calls other signature with the retrieval number in the cookie.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void placeOrder()
          throws ShoppingCartException, QuoteException, FrameworkException,
                 SQLException
  {
    if(logEnabled) { IBEUtil.log(CLASS, "placeOrder()", "CALLED");
}
    placeOrder(getRetrievalNumberString());
    if(logEnabled) { IBEUtil.log(CLASS, "placeOrder()", "DONE");
}
  }

  /**
   * This method submits the cart into an order.The method expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method.
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   *
   * Notes on behavior:
   * <LI>Minisite setting IBE_OM_PRICE_RECALCULATE determines whether the price gets recalculated or not.
   * <LI>Whether the order is created as booked or not is determined by the payment type
   * that has been saved into the quote.  This behavior depends on ASO Default Order State
   * profile value in all cases except when the payment type is 'Fax Credit Card'
   * in which case the Order will get created in the "Entered" state.
   * <LI>If the order gets created successfully, the orderNumber will be set and
   * can be retrieved via the getOrderNumber method.
   *
   * @param retrievalNumber in the event of an administrator placing the order, a
   * retrieval number must be passed to validate that the user has that role.
   * @throws FrameworkException If there is a framework layer error
   * @throws SQLException If there is a database error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of
   * of the error
   * @rep:displayname Place Order
   */
  public void placeOrder(String retrievalNumber)
          throws ShoppingCartException, QuoteException, FrameworkException,
                 SQLException
  {
    String  METHOD = "placeOrder";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "INPUT: retrievalNumber: " + retrievalNumber);
}
    boolean faxPayment = false;

    if((paymentType.equals(FAX_CC_PAYMENT))
            || (paymentType.equals(FAX_PO_PAYMENT)))
    {
      faxPayment = true;

    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId is " + cartId);
    IBEUtil.log(CLASS, METHOD, "paymentType is " + paymentType);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "faxPayment is " + faxPayment);

}
    try
    {

      /*
       * BigDecimal  contractQuoteId = null;
       * if((contractId != null) && (!contractId.equals("")))
       * {
       * if(logEnabled) { IBEUtil.log(CLASS, METHOD, "contractId is " + contractId);
}
       * cartId = contractId;
       * contractQuoteId = new BigDecimal(contractId);
       * }
       */
      BigDecimal  shareeNumber = makeRetrievalNumBigDecimal(retrievalNumber);

      setupHeaderRecord(makeCartIdBigDecimal(cartId),shareeNumber);
      forceTimestampValidation();

      String              priceRecalc =
        StoreMinisite.getMinisiteAttribute("IBE_OM_PRICE_RECALCULATE");
      SubmitControlRecord submitControlRec = new SubmitControlRecord();

      if(logEnabled) { 
      IBEUtil.log(CLASS, METHOD, "priceRecalc = " + priceRecalc);
}
      /*
       * Now the book flag is derieved in PL/SQl - 07/09
       * if((faxPayment) || "PO".equals(paymentType))
       * {
       * submitControlRec.book_flag = "F";
       * }
       * else
       * {
       * submitControlRec.book_flag = "F";
       * }
       */

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting book flag to gmiss");
}
      submitControlRec.book_flag = gMissChar;

      if("Y".equals(priceRecalc))
      {
        submitControlRec.calculate_price = "T";
      }
      else
      {
        submitControlRec.calculate_price = "F";
      }

      /*
       * AS per SKAMATH and SULEE, this is now done in PL/SQL
       * if(contractQuoteId != null)
       * {
       * Contract  contract[] = Contract.getContract(contractQuoteId);
       *
       * contract[0].setSigned();
       * }
       *
       */


      BigDecimal  partyId = null;
      BigDecimal  acctId = null;
      // sharee number had to be declared earlier for call to setupHeaderRecord
      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Sharee placing the order");
}
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.submit");
}
      orderHeaderRec = Quote.submit(headerRec.quote_header_id,
                                    headerRec.last_update_date, null, null,
                                    null, partyId, acctId, shareeNumber,
                                    RequestCtx.getMinisiteId(),
                                    submitControlRec);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling Quote.submit");

}
      if(orderHeaderRec.order_number != null)
      {
        orderNumber = orderHeaderRec.order_number.toString();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Order number is " + orderNumber);
}
      }
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   * 
   * @param METHOD
   * 
   * @see
   */
  private void logInputVariables(String METHOD)
  {
    if(logEnabled)
{ IBEUtil.log(CLASS, METHOD, "cartId is " + cartId);
    IBEUtil.log(CLASS, METHOD, "customerAccountId is " + customerAccountId);
   IBEUtil.log(CLASS, METHOD, "shipmentId is " + shipmentId);
    IBEUtil.log(CLASS, METHOD, 
                "soldtoContactPartyId is " + soldtoContactPartyId);
    IBEUtil.log(CLASS, METHOD,
                "shiptoCustomerAccountId is " + shiptoCustomerAccountId);
    IBEUtil.log(CLASS, METHOD, 
                "shiptoCustomerPartyId is " + shiptoCustomerPartyId);
    IBEUtil.log(CLASS, METHOD, 
                "shiptoContactPartyId is " + shiptoContactPartyId);
    IBEUtil.log(CLASS, METHOD, "shiptoPartySiteId is " + shiptoPartySiteId);
   IBEUtil.log(CLASS, METHOD, "shippingMethod is " + shippingMethod);
    IBEUtil.log(CLASS, METHOD, 
                "shippingInstructions is " + shippingInstructions);
    IBEUtil.log(CLASS, METHOD, 
                "packingInstructions is " + packingInstructions);
    IBEUtil.log(CLASS, METHOD, "paymentId is " + paymentId);
     IBEUtil.log(CLASS, METHOD, 
                "billtoCustomerAccountId is " + billtoCustomerAccountId);
    IBEUtil.log(CLASS, METHOD, 
                "billtoCustomerPartyId is " + billtoCustomerPartyId);
   IBEUtil.log(CLASS, METHOD, 
                "billtoContactPartyId is " + billtoContactPartyId);
    IBEUtil.log(CLASS, METHOD, "billtoPartySiteId is " + billtoPartySiteId);
   IBEUtil.log(CLASS, METHOD, "taxDetailId is " + taxDetailId);
    IBEUtil.log(CLASS, METHOD, "paymentType is " + paymentType);
   IBEUtil.log(CLASS, METHOD, "paymentNumber is " + paymentNumber);
    IBEUtil.log(CLASS, METHOD, "poNumber is " + poNumber);
}
  }

  /**
   * Convenience method to get the phone number and email address of an user.
   *
   *
   * @param partyId - the party id of the user
   *
   * @return - an array of Strings. <BR>
   * array[0] will contain the day time phone number<BR>
   * array[1] formerly contained the evening phone number, it now will only contain an empty string<BR>
   * array[2] formerly contained the fax number, it now will only contain an empty string<BR>
   * array[3] will contain the email address.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   */
  public static String[] getPrimaryEmailAndPhone(String partyId)
          throws FrameworkException, SQLException
  {
    boolean logEnabled = IBEUtil.logEnabled();  
    BigDecimal partyId_b = null;
    try {
      partyId_b = new BigDecimal(partyId);
    }
    catch (NumberFormatException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

    String METHOD        = "getPrimaryEmailAndPhone";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Call PartyManager.getPrimaryEmail & getPrimaryPhone with partyId = " + partyId);

}
    Email email               = PartyManager.getPrimaryEmail(partyId_b);
    Phone phone               = PartyManager.getPrimaryPhone(partyId_b);
    String emailStr           = "";
    String phoneNo            = "";
    if(email != null)emailStr = email.getEmailAddress();
    if(phone != null)
    {
          if (phone.getPhoneAreaCode() !=null && (!phone.getPhoneAreaCode().equals("")))
              phoneNo    += phone.getPhoneAreaCode()+"-";
          phoneNo        += phone.getPhoneNumber();
          if (phone.getPhoneExtension() !=null && (!phone.getPhoneExtension().equals("")))
              phoneNo    += " "+phone.getPhoneExtension();
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done  calling PartyManager.getPrimaryEmail & getPrimaryPhone");
}
    String[] contactPoints = new String[4];
    // as a fix for bug 2682452 - taking up new api getPrimaryEmailAndPhone
    // (used to call getPhoneAndEmail which returned an array of 4, new api only returns 2)
    // decided to still return array of 4 but blanks for fields we never used out of the box
    contactPoints[0] = phoneNo;
    contactPoints[1] = "";
    contactPoints[2] = "";
    contactPoints[3] = emailStr;

    return contactPoints;
  }


  /**
   * Method declaration
   * 
   * 
   * @param quoteHeaderId
   * 
   * @throws FrameworkException
   * @throws SQLException
   * 
   * @see
   */
  protected void setupHeaderRecord(BigDecimal quoteHeaderId)
          throws FrameworkException, SQLException
  {
    setupHeaderRecord(quoteHeaderId, RequestCtx.getShareeNumber());
  }

  protected void setupHeaderRecord(BigDecimal quoteHeaderId, BigDecimal retrievalNumber)
          throws FrameworkException, SQLException
  {
    String METHOD = "setupHeaderRecord";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    if(headerRec == null)
    {
      headerRec = new HeaderRecord();
    }

    if(headerRec.quote_header_id.equals(gMissNum))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "quoteHeaderId is " + quoteHeaderId);
}
      headerRec.quote_header_id = quoteHeaderId;
    }

    if(headerRec.currency_code.equals(gMissChar))
    {
      headerRec.currency_code = RequestCtx.getCurrencyCode();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "currency_code is " + headerRec.currency_code);
}
    }

    if(headerRec.price_list_id.equals(gMissNum))
    {
      headerRec.price_list_id = StoreMinisite.getPriceListID();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "headerRec.price_list_id is " + headerRec.price_list_id);
}
    }

    boolean isSharee = (retrievalNumber == null) ? false : true;

    if(!isSharee)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Not a sharee");
}
      if(headerRec.cust_account_id.equals(gMissNum))
      {
        headerRec.cust_account_id = RequestCtx.getAccountId();
      }

      if(headerRec.party_id.equals(gMissNum))
      {
        headerRec.party_id = RequestCtx.getPartyId();
      }
    }

    /*
     * if ( (RequestCtx.userIsSalesRep() ) ||
     * (RequestCtx.getShareeNumber() != null) )
     */

//    if(isSharee)
//    {
// do this time stamp check for everyone now
      if((lastModifiedTimestamp != null)
              && (!lastModifiedTimestamp.equals(EMPTY_STRING)))
      {
        headerRec.last_update_date = Timestamp.valueOf(lastModifiedTimestamp);
      }
//    }

    if((expirationTimestamp != null)
            && (!expirationTimestamp.equals(EMPTY_STRING)))
    {
      headerRec.quote_expiration_date =
        Timestamp.valueOf(expirationTimestamp);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   *
   * @param quoteHeaderId
   * @param operationCode
   *
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupLineRecords(BigDecimal quoteHeaderId, String operationCode, boolean setupLineBillingInfo)
          throws ShoppingCartException
  {
    String METHOD = "setupLineRecords";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    String billtoContactPartyId = null;
    String billtoPartySiteId = null;
    String billtoCustomerAccountId = null;
    String billtoPartySiteType = null;
    try {
    if(shopCartItems != null)
    {
      int numCartItems = shopCartItems.length;

      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems = " +  numCartItems);
      IBEUtil.log(CLASS, METHOD, "quoteHeaderId = " + quoteHeaderId);
}
      if(lineRec == null)
      {
        lineRec = new LineRecord[numCartItems];
      }

      for(int i = 0; i < numCartItems; i++)
      {
        if(lineRec[i] == null)
        {
          lineRec[i] = new LineRecord();
        }
//-------------------- MAIN LINE INFO ----------------------------------------//
        lineRec[i].operation_code = operationCode;
        lineRec[i].quote_header_id = quoteHeaderId;
        lineRec[i].quote_line_id =
          new BigDecimal(shopCartItems[i].cartLineId);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "quoteLineId = " + shopCartItems[i].cartLineId);
}
        if (setupLineBillingInfo) {
//-------------------- LINE BILLING INFO -------------------------------------//
          billtoContactPartyId = shopCartItems[i].billtoContactPartyId;
          billtoPartySiteId = shopCartItems[i].billtoPartySiteId;
          billtoCustomerAccountId = shopCartItems[i].billtoCustomerAccountId;
          billtoPartySiteType = shopCartItems[i].billtoPartySiteType;

          //----------- LINE BILLING ADDRESS (PARTY SITE ID) -------------------//
          if((billtoPartySiteId != null) &&!billtoPartySiteId.equals(""))
          {
            lineRec[i].invoice_to_party_site_id =
              new BigDecimal(billtoPartySiteId);
          }
          else if((billtoContactPartyId != null)
                  && (billtoCustomerAccountId != null))
          {
            throw new ShoppingCartException("IBE_SC_NO_BILL_ADDRS");
          }

          //------------ LINE BILLING CONTACT (PARTY ID) -----------------------//
          if((billtoContactPartyId != null) && (!billtoContactPartyId.equals("")))
          {
            lineRec[i].invoice_to_party_id =
              new BigDecimal(billtoContactPartyId);
          }
          else if(billtoContactPartyId == null)
          {
            lineRec[i].invoice_to_party_id  = null;

            if((billtoPartySiteType != null) && (!billtoPartySiteType.equals(""))
                    && (billtoPartySiteType.equals("contact")))
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                "nulling invoice_to_party_site_id cos the addrs belongs to the contact");

}
              lineRec[i].invoice_to_party_site_id = null;
            }
          }

          //------------ LINE BILLING CUSTOMER (ACCOUNT ID) --------------------//
          if((billtoCustomerAccountId != null)
                  &&!("".equals(billtoCustomerAccountId)))
          {
            lineRec[i].invoice_to_cust_account_id =
            new BigDecimal(billtoCustomerAccountId);
          }
          else
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "nulling invoice_to_cust_account_id");

}
            lineRec[i].invoice_to_cust_account_id = null;

            if(billtoCustomerAccountId == null)
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                "nulling invoice_to_party_site_id and invoice_to_party_id");

}
              lineRec[i].invoice_to_party_site_id = null;
              lineRec[i].invoice_to_party_id = null;
            }
          }
        }
      } // end if setupLineBillingInfo
    } // end if(shopCartItems != null)
    } // end try
    catch (NumberFormatException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }




  /**
   * Method declaration
   *
   *
   * @param scartItem
   * @param itemQuantity
   * @param operationCode
   * @param lineIndex
   * @param lineDtlIndex
   *
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupServices(ShoppingCartItem scartItem,
                             BigDecimal itemQuantity, String operationCode,
                             int[] lineIndex,
                             int[] lineDtlIndex) throws ShoppingCartException
  {
    // for each service in scartitem, populate appropriate fields in lineRec
    // lineDetailRec.
    String  METHOD = "setupServices";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "item quantity = " + itemQuantity );
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "operationCode = " + operationCode );

}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineIndex[0] is " + lineIndex[0]);

}
    boolean createOp = false;

    if(operationCode.equals(ShoppingCart.CREATE_OPCODE))
    {
      createOp = true;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineDtlIndex[0] is " + lineDtlIndex[0]);
}
    }

    if(scartItem.svcItems != null)
    {
      int svcItemsCount = scartItem.svcItems.length;

      if(svcItemsCount > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "svc Items Count = " + svcItemsCount );
}
        BigDecimal  quoteLineIndexOrId = null;
        BigDecimal  svaItemLineIndex = 
          new BigDecimal((double) (lineIndex[0]));
        BigDecimal  quoteLineId = null;
        BigDecimal  quoteHeaderId = new BigDecimal(cartId);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId = " + cartId );

}
        // BigDecimal  shipmentIdBig = null;

        for(int i = 0; i < svcItemsCount; i++)
        {

          if(createOp)
          {
            quoteLineIndexOrId = new BigDecimal((double) (lineIndex[0] + 1));
            quoteLineId = null;

            if(lineDetRec[lineDtlIndex[0]] == null)
            {
              lineDetRec[lineDtlIndex[0]] = 
                new oracle.apps.ibe.shoppingcart.quote.LineDetailRecord();
            }

            setupLineDetailRecord(lineDetRec[lineDtlIndex[0]], 
                                  scartItem.svcItems[i], operationCode, 
                                  quoteLineIndexOrId, svaItemLineIndex);

            lineDtlIndex[0]++;
            // shipmentIdBig = null;

          }
          else
          {
            quoteLineIndexOrId = 
              new BigDecimal(scartItem.svcItems[i].cartLineId);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,
              "BEing updated, cartLineId = " + scartItem.svcItems[i].cartLineId );
}
            quoteLineId = quoteLineIndexOrId;
            // shipmentIdBig = new BigDecimal(scartItem.svcItems[i].shipmentId);
          }

          if(lineRec[lineIndex[0]] == null)
          {
            lineRec[lineIndex[0]] = 
              new oracle.apps.ibe.shoppingcart.quote.LineRecord();
          }

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "line rcrd being set up");
}
          ShoppingCartUtil.setupLineRecord(lineRec[lineIndex[0]],
            operationCode, quoteHeaderId,
            quoteLineId, itemQuantity,
            new BigDecimal(scartItem.svcItems[i].inventoryItemId),
            new BigDecimal(scartItem.svcItems[i].organizationId),
            scartItem.svcItems[i].uom,
            ShoppingCartItem.SERVICE_ITEM_TYPE, null, null, null);

          /*
           * if(lineShipmentRec[lineIndex[0]] == null)
           * {
           * lineShipmentRec[lineIndex[0]] =
           * new oracle.apps.ibe.shoppingcart.quote.ShipmentRecord();
           * }
           * setupLineShipmentRecord(lineShipmentRec[lineIndex[0]],
           * operationCode, quoteHeaderId,
           * quoteLineIndexOrId, shipmentIdBig,
           * itemQuantity);
           */

          lineIndex[0]++;

        }   // end of for loop
      }     // end of svc items length > 0
    }       // end of svc items != null

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   *
   * @param lineDetailRecord
   * @param svcItem
   * @param operationCode
   * @param quoteLineOrIndexId
   * @param svaItemLineIndex
   *
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupLineDetailRecord(LineDetailRecord lineDetailRecord,
                                     ServiceItem svcItem,
                                     String operationCode,
                                     BigDecimal quoteLineOrIndexId,
                                     BigDecimal svaItemLineIndex)
                                     throws ShoppingCartException
  {
    setupLineDetailRecord(lineDetailRecord, svcItem, operationCode,
                          quoteLineOrIndexId, svaItemLineIndex, null);
  }

  /**
   * Method declaration
   *
   *
   * @param lineDetailRecord
   * @param svcItem
   * @param operationCode
   * @param quoteLineOrIndexId
   * @param svaItemLineIndex
   * 
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupLineDetailRecord(LineDetailRecord lineDetailRecord,
                                     ServiceItem svcItem,
                                     String operationCode,
                                     BigDecimal quoteLineDetailOrIndexId,
                                     BigDecimal svaItemLineIndex,
                                     BigDecimal svaQuoteLineId)
                                     throws ShoppingCartException
  {
    String  METHOD = "setupLineDetailRecord";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "operationCode is " + operationCode);
    IBEUtil.log(CLASS, METHOD,
                "quoteLineOrIndexId is " + quoteLineDetailOrIndexId);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "svaItemLineIndex is " + svaItemLineIndex);
    IBEUtil.log(CLASS, METHOD, "svaQuoteLineId is " + svaQuoteLineId);
}
    lineDetailRecord.operation_code = operationCode;
    try {
    if(operationCode.equals(CREATE_OPCODE))
    {
      if(svaQuoteLineId != null)
      {
        lineDetailRecord.service_ref_line_id = svaQuoteLineId;
      }
      else
      {
        lineDetailRecord.service_ref_qte_line_index = svaItemLineIndex;
      }

      lineDetailRecord.qte_line_index = quoteLineDetailOrIndexId;
      lineDetailRecord.service_ref_type_code = svcItem.getServiceReferenceType();
      if (EMPTY_STRING.equals(lineDetailRecord.service_ref_type_code))
      {
        // for backward compatability and default behavior
        lineDetailRecord.service_ref_type_code = ServiceItem.REFTYPE_QUOTE;
      }
    }
    else
    {
      lineDetailRecord.quote_line_id = new BigDecimal(svcItem.cartLineId);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
        "svc item quotelineid is " + lineDetailRecord.quote_line_id);
}
      lineDetailRecord.quote_line_detail_id = quoteLineDetailOrIndexId;
    }

    // lineDetailRecord.service_ref_type_code = svcItem.serviceReferenceType;
    lineDetailRecord.service_period = svcItem.servicePeriod;

    if(!svcItem.serviceDuration.equals(EMPTY_STRING))
    {
      BigDecimal  svcDur = new BigDecimal(svcItem.serviceDuration.trim());

      if(svcDur.doubleValue() <= 0)
      {
        throw new ShoppingCartException("IBE_SC_NEG_SVC_DURATION");

      }

      lineDetailRecord.service_duration = svcDur;
    }
    } // end try block
    catch (NumberFormatException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

  }

  /**
   * Method declaration
   *
   *
   * @param lineDetailRecord
   * @param svcItem
   * @param operationCode
   * @param quoteLineOrIndexId
   * @param svaQuoteLineId
   *
   * @throws FrameworkException
   * @throws SQLException
   * @throws ShoppingCartException
   *
   * @see
   */
  private void setupLineDetailRecord(LineDetailRecord lineDetailRecord,
                                     Item svcItem, String operationCode,
                                     BigDecimal quoteLineOrIndexId,
                                     BigDecimal svaQuoteLineId) throws
                                     ShoppingCartException, SQLException,
                                     FrameworkException
  {
    String  METHOD = "setupLineDetailRecord";
    try {
    if(operationCode.equals(CREATE_OPCODE))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "operationCode is " + operationCode);
      IBEUtil.log(CLASS, METHOD,
                  "quoteLineOrIndexId is " + quoteLineOrIndexId);
}
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "quoteLineOrIndexId is " + quoteLineOrIndexId);
      IBEUtil.log(CLASS, METHOD, "svaQuoteLineId is " + svaQuoteLineId);
}
      lineDetailRecord.operation_code = operationCode;
      lineDetailRecord.service_ref_line_id = svaQuoteLineId;
      lineDetailRecord.qte_line_index = quoteLineOrIndexId;
      lineDetailRecord.service_period = svcItem.getSrvcPeriod();
      lineDetailRecord.service_ref_type_code = "QUOTE";

      if(svcItem.getSrvcDuration() != -1)
      {
        lineDetailRecord.service_duration =
          new BigDecimal((double) svcItem.getSrvcDuration());

      }
    }
    else
    {
      throw new ShoppingCartException("IBE_SC_INVALID_OPERATION");
    }
    } // end try block
    catch (NumberFormatException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch (StringIndexOutOfBoundsException e) {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

  }

  /**
   * Method declaration
   * 
   * 
   * @param scartItem
   * @param itemQuantity
   * @param lineIndex
   * @param lineDtlIndex
   * @param createRow
   * @param deleteRow
   * @param updateRow
   *
   * @throws FrameworkException
   * @throws SQLException
   * @throws ShoppingCartException
   * 
   * @see
   */
  private void setupServices(ShoppingCartItem scartItem,
                             BigDecimal itemQuantity, int[] lineIndex, 
                             int[] lineDtlIndex, ArrayList createRow, 
                             ArrayList deleteRow, ArrayList updateRow) throws ShoppingCartException,
                             FrameworkException, SQLException
  {
    // for each service in scartitem, populate appropriate fields in lineRec
    // lineDetailRec.
    String  METHOD = "setupServices";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    int numCreateRows = 0;

    if(createRow != null)
    {
      numCreateRows = createRow.size();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of services to be created = " + numCreateRows);
}
    }

    int numDeleteRows = 0;

    if(deleteRow != null)
    {
      numDeleteRows = deleteRow.size();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of services to be deleted = " + numDeleteRows);
}
    }

    int numUpdateRows = 0;

    if(updateRow != null)
    {
      numUpdateRows = updateRow.size();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of services to be updated = " + numUpdateRows);
}
    }

    BigDecimal  quoteLineIndex = null;
    BigDecimal  svaQuoteLineId = new BigDecimal(scartItem.cartLineId);
    BigDecimal  quoteLineId = null;
    BigDecimal  quoteHeaderId = new BigDecimal(cartId);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
    IBEUtil.log(CLASS, METHOD, "cartLineId = " + scartItem.cartLineId);
}
    // BigDecimal  shipmentIdBig = null;

    if(numDeleteRows > 0)
    {
      int svcItemsCount = scartItem.svcItems.length;

      for(int i = 0; i < svcItemsCount; i++)
      {
        if(deleteRow.contains(String.valueOf(i)))
        {
          quoteLineId = new BigDecimal(scartItem.svcItems[i].cartLineId);

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Being DELETED quteLineId " + quoteLineId);

}
          if(lineRec[lineIndex[0]] == null)
          {
            lineRec[lineIndex[0]] =
              new oracle.apps.ibe.shoppingcart.quote.LineRecord();
          }

          ShoppingCartUtil.setupLineRecord(lineRec[lineIndex[0]],
                                           DELETE_OPCODE, quoteHeaderId,
                                           quoteLineId, null, null, null,
                                           null, null, null, null, null);

          lineIndex[0]++;
        }
      }
    }

    if(numUpdateRows > 0)
    {
      int svcItemsCount = scartItem.svcItems.length;

      for(int i = 0; i < svcItemsCount; i++)
      {
        if(updateRow.contains(String.valueOf(i)))
        {
          quoteLineId = new BigDecimal(scartItem.svcItems[i].cartLineId);

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Being UPDATED quteLineId " + quoteLineId);

}
          if(lineRec[lineIndex[0]] == null)
          {
            lineRec[lineIndex[0]] =
              new oracle.apps.ibe.shoppingcart.quote.LineRecord();
          }

          ShoppingCartUtil.setupLineRecord(lineRec[lineIndex[0]],
                                           UPDATE_OPCODE, quoteHeaderId,
                                           quoteLineId, itemQuantity, null, null,
                                           null, null, null, null, null);

          lineIndex[0]++;
        }
      }
    }

    if(numCreateRows > 0)
    {
      BigDecimal  orgId = RequestCtx.getOrganizationId();

      for(int i = 0; i < numCreateRows; i++)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineIndex[0] :" + lineIndex[0]);
}
        if(lineRec[lineIndex[0]] == null)
        {
          lineRec[lineIndex[0]] =
            new oracle.apps.ibe.shoppingcart.quote.LineRecord();
        }

        quoteLineIndex = new BigDecimal((double) (lineIndex[0] + 1));

        int   itemId = Integer.parseInt((String) createRow.get(i));
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Being added is service itemId " + itemId);

}
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Item.load");
}
        Item  svcCatalogItem = Item.load(itemId, Item.SHALLOW, false,false);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling Item.load");

}
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
          "PrimaryUOMCode = " + svcCatalogItem.getPrimaryUOMCode() );
        IBEUtil.log(CLASS, METHOD, "item quantity = " + itemQuantity);
}
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Line rcrd being set up");
}
        ShoppingCartUtil.setupLineRecord(lineRec[lineIndex[0]],
                                         CREATE_OPCODE, quoteHeaderId,
                                         quoteLineIndex, itemQuantity,
                                         new BigDecimal((double) svcCatalogItem.getItemID()),
                                         orgId,
                                         svcCatalogItem.getPrimaryUOMCode(),
                                         ShoppingCartItem.SERVICE_ITEM_TYPE,
                                         null, null, null);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Line rcrd set up done");

}
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineDtlIndex[0] :" + lineDtlIndex[0]);
}
        if(lineDetRec[lineDtlIndex[0]] == null)
        {
          lineDetRec[lineDtlIndex[0]] =
            new oracle.apps.ibe.shoppingcart.quote.LineDetailRecord();
        }

        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Line dtl rcrd being set up");
}
        setupLineDetailRecord(lineDetRec[lineDtlIndex[0]], svcCatalogItem,
                              CREATE_OPCODE, quoteLineIndex, svaQuoteLineId);

        lineDtlIndex[0]++;
        lineIndex[0]++;

      }   // end of for loop
    }     // end of numCreateRows > 0

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Loads all the saved carts for the logged in user. This api no longer involves
   * the ASO_USE_CONTRACTS. This API does not load carts that have contracts associated with them.
   *
   * @param includeAllVersions -  true if all the versions of cart number
   * should be included; false if only the latest version of cart number
   * should be included.
   * @param includeOrdered - true if the ordered carts should be included; false otherwise.
   * @param loadDistinct - true if the different versions of the same cart number (cart)
   * should be returned seperately or false if the versions need to be set in the
   * parent cart, which can then be later retrieved by calling the getVersions method
   * on that cart. If 'includeAllVersions' is set to false, then the loadDistinct
   * parameter is implicitly false.
   * @return - an array of ShoppingCart. Only the header information is loaded in
   * each cart.
   * <BR> If 'includeAllVersions' is true and 'loadDistinct' is false, only the
   * highest versions are returned. You can get all versions of the cart by calling
   * the getVersions method on each returned cart.
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  public static ShoppingCart[] loadAllCarts(boolean includeAllVersions,
                                            boolean includeOrdered,
                                            boolean loadDistinct) throws SQLException,
                                            FrameworkException,
                                            QuoteException,
                                            ShoppingCartException
  {
    // formerly, this would check IBEUtil.useFeature("ASO_USE_CONTRACTS"); but for bug 3325723, now it's just false
    boolean loadContractCarts = false;

    return loadAllCarts(includeAllVersions, includeOrdered, loadDistinct, 
                        loadContractCarts);
  }

  /**
   * Loads all the saved carts for the logged in user.
   * 
   * 
   * @param includeAllVersions -  true if all the versions of cart number
   * should be included; false if only the latest version of cart number
   * should be included.
   * @param includeOrdered - true if the ordered carts should be included; false otherwise.
   * @param loadDistinct - true if the different versions of the same cart number (cart)
   * should be returned seperately or false if the versions need to be set in the
   * parent cart, which can then be later retrieved by calling the getVersions method
   * on that cart. If 'includeAllVersions' is set to false, then the loadDistinct
   * parameter is implicitly false.
   * @param loadContractCarts - should be set to false if carts that have contracts
   * associated with them should not be loaded.
   * @return - an array of ShoppingCart. Only the header information is loaded in
   * each cart.
   * <BR> If 'includeAllVersions' is true and 'loadDistinct' is false, only the
   * highest versions are returned. You can get all versions of the cart by calling
   * the getVersions method on each returned cart.
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */

  public static ShoppingCart[] loadAllCarts(boolean includeAllVersions,
                                            boolean includeOrdered,
                                            boolean loadDistinct,
                                            boolean loadContractCarts) throws SQLException,
                                            FrameworkException,
                                            QuoteException,
                                            ShoppingCartException
  {
    return loadAllCarts(includeAllVersions,includeOrdered,loadDistinct,
                        loadContractCarts,LOAD_CART);
  }
  // make this public or private?
  private static ShoppingCart[] loadAllCarts(boolean includeAllVersions,
                                            boolean includeOrdered,
                                            boolean loadDistinct,
                                            boolean loadContractCarts,
                                            int loadType) throws SQLException,
                                            FrameworkException,
                                            QuoteException,
                                            ShoppingCartException
  {
    String          METHOD = "loadAllCarts";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) {
      IBEUtil.log(CLASS, METHOD,"CALLED with input:");
      IBEUtil.log(CLASS, METHOD,"  includeAllVersions : " + includeAllVersions);
      IBEUtil.log(CLASS, METHOD,"  includeOrdered     : " + includeOrdered);
      IBEUtil.log(CLASS, METHOD,"  loadDistinct       : " + loadDistinct);
      IBEUtil.log(CLASS, METHOD,"  loadContractCarts  : " + loadContractCarts);
      IBEUtil.log(CLASS, METHOD,"  loadType           : " + loadType);
    }
    BigDecimal      partyID = RequestCtx.getPartyId();
    BigDecimal      custAcctID = RequestCtx.getAccountId();
    Class           cartClass = null;
    ShoppingCart[]  scarts = null;

    if(!includeAllVersions)
    {
      loadDistinct = false;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Dont include all versions");
}
    }

    try
    {
      cartClass =
        Class.forName("oracle.apps.ibe.shoppingcart.quote.ShoppingCart");
    }
    catch(Exception e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  " Could not get class def for ShoppingCart!!: "
                  + e.getMessage());

}
      return null;
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Abt to call Quote.loadAll");
}
    Quote[] allCarts = loadAll(partyID, custAcctID, includeAllVersions,
                               includeOrdered, loadContractCarts, cartClass, loadType);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Done calling Quote.loadAll");

}
    if(allCarts != null)
    {
      int allCartsLength = allCarts.length;

      if(allCartsLength > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num cart loaded is " + allCartsLength);
}
        scarts = new ShoppingCart[allCartsLength];

        /*
         * for (int i = 0; i < allCartsLength; i++)
         * scarts[i] = (ShoppingCart) allCarts[i];
         */

        System.arraycopy(allCarts, 0, scarts, 0, allCartsLength);
      }
      else
      {
        return null;
      }
    }
    else
    {
      return null;
    }
    // load contract information
    // eventually, we'd like to do this in one batch load api, but for now we'll
    // settle for removing the complexity from the jsp layer
    if (loadContractCarts && (scarts != null) && (scarts.length > 0)) {
      for (int i = 0; i < scarts.length; i++) {
        if ((scarts[i] != null) && (scarts[i].headerRec != null) &&
            (scarts[i].headerRec.quote_header_id != null) && (scarts[i].headerRec.quote_header_id != gMissNum)) {
          scarts[i].associatedContract = Contract.getContract(scarts[i].headerRec.quote_header_id);
        }
      }

    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to call sortVersions");
}
    return ShoppingCartUtil.sortVersions(scarts, includeAllVersions, loadDistinct);
  }


  /**
   * Loads all the items and version for a given cart number. The API populates
   * the items for cart whose versionNumber is specified. The other versions of
   * the cart will only have header information (name, number, version etc). Line
   * level taxes are not loaded by this method.
   * @param cartNumber -  the cart number of the cart to be loaded
   * @param versionNumber - the version of the cart for which the items are to be
   * loaded.
   * @param populateOtherVersions - if true, then the items are loaded for all
   * versions of the cart. This is very <B>expensive</B> and should be used
   * with caution. If this is not needed, pass in false.
   * @return - a ShoppingCart with all the items and versions loaded. Will be
   * null if there is an error loading the cart. The versions can be retrieved
   * by calling the getVersions method on the returned cart. The versions will
   * only have header information (name, number, version etc).
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   * @deprecated versioning is no longer supported   
   */
  // not used in iStore code as of IBE.P    
  public static ShoppingCart loadWithItemsAndVersions(String cartNumber,
          String versionNumber, 
          boolean populateOtherVersions) throws SQLException, 
          FrameworkException, QuoteException, ShoppingCartException
  {
    return loadWithItemsAndVersions(cartNumber, versionNumber, 
                                    populateOtherVersions, false);
  }

  /**
   * Loads all the items and version for a given cart number. The API populates
   * the items for cart whose versionNumber is specified. The other versions of
   * the cart will only have header information (name, number, version etc).
   * @param cartNumber -  the cart number of the cart to be loaded
   * @param versionNumber - the version of the cart for which the items are to be
   * loaded.
   * @param populateOtherVersions - if true, then the items are loaded for all
   * versions of the cart. This is very <B>expensive</B> and should be used
   * with caution. If this is not needed, pass in false.
   * @return - a ShoppingCart with all the items and versions loaded. Will be
   * null if there is an error loading the cart. The versions can be retrieved
   * by calling the getVersions method on the returned cart. The versions will
   * only have header information (name, number, version etc).
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   * @deprecated versioning is no longer supported
   */
  // not used in iStore code as of IBE.P
  public static ShoppingCart loadWithItemsAndVersions(String cartNumber,
          String versionNumber, boolean populateOtherVersions,
          boolean loadLineLevelTaxInfo) throws SQLException,
          FrameworkException, QuoteException, ShoppingCartException
  {
    return loadWithItemsAndVersions( cartNumber,
           versionNumber,  populateOtherVersions,
           loadLineLevelTaxInfo, true);
  }

  /**
   * @deprecated versioning is no longer supported
   */
  // new signature added for bug 2747903 (branch) - to allow validation to be turned
  // off where appropriate
  // not used in iStore code as of IBE.P   
  public static ShoppingCart loadWithItemsAndVersions(String cartNumber,
          String versionNumber, boolean populateOtherVersions,
          boolean loadLineLevelTaxInfo, boolean validateUser) throws SQLException,
          FrameworkException, QuoteException, ShoppingCartException
  {
    String          METHOD = "loadWithItemsAndVersions";
    boolean logEnabled = IBEUtil.logEnabled();  
    Class           scClass = null;
    ShoppingCart[]  cartVersions = null;
    ShoppingCart    shopCart = null;
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD,
      "populateOtherVersions is = " + populateOtherVersions);
}
    try
    {
      scClass =
        Class.forName("oracle.apps.ibe.shoppingcart.quote.ShoppingCart");
    }
    catch(ClassNotFoundException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  " Could not get class def for ShoppingCart!!");

}
      return null;
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to call Quote.loadVersions");
}
    Quote[] scarts = loadVersions(new BigDecimal(cartNumber),RequestCtx.getPartyId(),
                                       RequestCtx.getAccountId(), RequestCtx.getMinisiteId(), false, validateUser, scClass);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done call Quote.loadVersions");

}
    boolean versionNotFound = true;
    String  maxVersionNumber = "";

    if(scarts != null)
    {
      int qlength = scarts.length;

      if(qlength > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num of carts: " + qlength);
}
        cartVersions = new ShoppingCart[qlength];

        for(int i = 0; i < qlength; i++)
        {
          cartVersions[i] = (ShoppingCart) scarts[i];
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "i = " + i);

}
          if(populateOtherVersions)
          {
            String  tCartId = cartVersions[i].cartId;

            CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
            loadControlRec.loadItems       = true;
            loadControlRec.fillChildItems  = true;
            loadControlRec.showPrice       = true;
            loadControlRec.defaultPreferences = true;            
            loadControlRec.loadType        = ShoppingCart.LOAD_CART;
            loadControlRec.validateUser    = validateUser;

            cartVersions[i] = loadAndFill(tCartId, null,null,loadControlRec);

            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "back from loadAndFill cartVersions[i]: " + cartVersions[i]);
}
/*
            cartVersions[i] = new ShoppingCart(tCartId, true, true, false,
                                               false, false, false, false,
                                               false, false, false, true,
                                               false, false);

            if((cartVersions[i] != null)
                    && (cartVersions[i].headerRec != null))
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Loading items");
}
//              loadItems(cartVersions[i], false, false, loadLineLevelTaxInfo, false);
            }
*/
            if (cartVersions[i] == null || cartVersions[i].headerRec == null)
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          "At iteration " + i
                          + " either cart or cart header is NULL. The required cart id was: "
                          + tCartId);

}
              throw new ShoppingCartException("IBE_SC_ERR_LOADING_VERS");
            }
          }
          else if((cartVersions[i] != null)
                  && (cartVersions[i].headerRec != null))
          {

            cartVersions[i].fillHeaderInformation();

            if(i == 0)
            {
              maxVersionNumber = cartVersions[i].versionNumber;
            }
          }
          else
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                        "At iteration " + i
                        + " either cart or cart header is NULL");

}
            throw new ShoppingCartException("IBE_SC_ERR_LOADING_VERS");
          }

          // determine the right version
          if((versionNotFound)
                  && (cartVersions[i].versionNumber.equals(versionNumber)))
          {
            shopCart = cartVersions[i];
            versionNotFound = false;
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Needed version has been found!");
}
          }
        }   // for loop
      }     // if scarts not null

      if(versionNotFound)
      {
        throw new ShoppingCartException("IBE_SC_VERSION_NOT_FOUND");
      }

      String  tCartId = shopCart.cartId;
      CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
      loadControlRec.loadItems       = true;
      loadControlRec.fillChildItems  = true;
      loadControlRec.showPrice       = true;
      loadControlRec.defaultPreferences = true;      
      loadControlRec.loadType        = ShoppingCart.LOAD_CART;
      loadControlRec.validateUser    = validateUser;

      shopCart = loadAndFill(tCartId, null,null, loadControlRec);
/*
      shopCart = new ShoppingCart(tCartId, true, true, false, false, false,
                                  false, false, false, false, false, true,
                                  false, false);
*/
      if((shopCart != null) && (shopCart.headerRec != null))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart seems to be okay");
}
//        loadItems(shopCart, false, false, loadLineLevelTaxInfo, false);

        shopCart.versions = cartVersions;
        shopCart.latestVersionNumber = maxVersionNumber;
      }
      else
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                    " either cart or cart header is NULL. "
                    + " The required cart id was: " + tCartId);

}
        throw new ShoppingCartException("IBE_SC_ERR_LOADING_VERS");
      }
    }
    else    // qlength < 1
    {
      return null;
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
    return shopCart;
  }

  /**
   * Activates the cart whose cart id is passed in, that is, makes the cart as
   * the active cart of the user account. A copy of the cart is made and nominated
   * as the active cart for the user.
   * @param cartId -  the cart id of the cart to be activated
   * @param lastUpdateTimestamp - the last update time stamp of the cart which
   * is being activated
   * @return - the cart id of the activated cart.
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  // not used in iStore code as of IBE.P   
  public static String activateCart(String cartId, String lastModifiedTimestamp)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    BigDecimal  activatedId = null;

    try
    {
      Timestamp last_update_date = null;
      if((lastModifiedTimestamp != null)
              && (!lastModifiedTimestamp.equals(EMPTY_STRING)))
      {
        last_update_date = java.sql.Timestamp.valueOf(lastModifiedTimestamp);
      }

      activatedId =
        Quote.activate(new BigDecimal(cartId),
                       last_update_date, true);
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    if(activatedId == null)
    {
      throw new ShoppingCartException("IBE_SC_UNABLE_TO_ACTIVATE");

    }

    return activatedId.toString();
  }

  /** This original signature keeps the original behavior which is to
   *  do a real delete of the quote record.  New signature has a flag to allow
   *  for expiring/invalidating of the quote.
   */
  public static void delete(String cartId, String lastUpdateTimestamp)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    ShoppingCart.delete(cartId,lastUpdateTimestamp, true);
  }

  /**
   * Deletes the cart.
   *
   * @param cartId The unique identifier of the cart.
   * @param lastUpdateTimestamp The last modified timestamp of the cart.
   * @param expunge The flag to indicate whether the cart needs to be deleted; true to delete the cart, or false to expire and invalidate the cart.
   * @throws FrameworkException if there is a framework layer error
   * @throws QuoteException the error message will indicate the nature of the error
   * @throws ShoppingCartException the error message will indicate the nature of the error
   * @throws SQLException if there is a database error
   * @rep:displayname Delete Cart
   */
  public static void delete(String cartId, String lastUpdateTimestamp, boolean expunge)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    // for now, just ignores expunge flag & calls original delete
    java.sql.Timestamp  updateTimestamp = null;

    if((lastUpdateTimestamp != null)
            && (!lastUpdateTimestamp.equals(EMPTY_STRING)))
    {
      updateTimestamp = java.sql.Timestamp.valueOf(lastUpdateTimestamp);

    }

    try
    {
      Quote.delete(new BigDecimal(cartId), updateTimestamp, RequestCtx.getMinisiteId(), expunge);
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
  }

  /**
   * Appends a cart to another cart and saves sharees' information. The API
   * creates a new version of the cart <CODE>appendToCartId</CODE> and appends the
   * cart <CODE>cartId</CODE> to it.
   * <P>
   * The optional parameters, <CODE>cartPassword</CODE>, <CODE>url</CODE>,
   * <CODE>emailAddresses</CODE>, and <CODE>privilegeTypes</CODE>, are to share the
   * cart.
   * <P> Shipping and tax charges are calculated during this operation. <P>
   * @param cartId  ID of the appending cart
   * @param lastUpdateTimestamp Optional.  The last update date of the cart
   * header. If it is not null, a check is done to verify that there was
   * no update to the cart header after the given last update date.
   * @param appendToCartId  ID of the cart to which the cart
   * <CODE>cartId</CODE> is appended to
   * @param cartPassword  optional; password the sharees should use to access
   * the cart <CODE>appendToCartId</CODE>
   * @param url  optional; URL to access the shared cart
   * @param emailAddresses  optional; email addresses of the sharees
   * @param privilegeTypes  optional; privilege types of the sharees
   * @param comment  optional; comments to be sent in the email notification
   * to the persons who are being granted access to the cart.
   * @return cart ID of the appended cart
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  // not used in iStore code as of IBE.P   
  public static String appendToAndShare(String cartId,
                                        String lastUpdateTimestamp,
                                        String appendToCartId,
                                        String cartPassword, String url,
                                        String[] emailAddresses,
                                        String[] privilegeTypes,
                                        String comment) throws SQLException,
                                        FrameworkException, QuoteException,
                                        ShoppingCartException
  {

    java.sql.Timestamp  updateTimestamp = null;

    if((lastUpdateTimestamp != null)
            && (!lastUpdateTimestamp.equals(EMPTY_STRING)))
    {
      updateTimestamp = java.sql.Timestamp.valueOf(lastUpdateTimestamp);

    }

    oracle.apps.ibe.shoppingcart.quote.ControlRecord  controlRecord =
      new oracle.apps.ibe.shoppingcart.quote.ControlRecord();

    ShoppingCartUtil.setupControlRecord(controlRecord, NO, NO);

    BigDecimal  newCartId = null;

    try
    {

      newCartId = Quote.appendToAndShare(new BigDecimal(cartId),
                                         RequestCtx.getPartyId(),
                                         RequestCtx.getAccountId(),
                                         RequestCtx.getShareeNumber(),
                                         null, updateTimestamp,
                                         new BigDecimal(appendToCartId),
                                         true, true, Quote.SEPARATE_LINES,
                                         cartPassword, url, emailAddresses,
                                         privilegeTypes, comment,
                                         StoreMinisite.getPriceListID(),
                                         RequestCtx.getCurrencyCode(),
                                         controlRecord);
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    if(newCartId == null)
    {
      throw new ShoppingCartException("IBE_SC_UNABLE_TO_APPEND");

    }

    return newCartId.toString();

  }

  /**
   * Replaces a cart with another cart and saves sharees' information. The API
   * creates a new version of <CODE>replaceCartId</CODE>.
   * <P>
   * The optional parameters, <CODE>cartPassword</CODE>, <CODE>url</CODE>,
   * <CODE>emailAddresses</CODE>, and <CODE>privilegeTypes</CODE>, are to share the
   * cart.
   * <P>
   * <P> Shipping and tax charges are calculated during this operation. <P>
   * @param cartId  ID of the replacing cart
   * @param lastUpdateTimestamp Optional.  The last update date of the cart
   * header. If it is not null, a check is done to verify that there was
   * no update to the cart header after the given last update date.
   * @param replaceCartId  ID of the cart which the cart
   * <CODE>cartId</CODE> is replacing
   * @param cartPassword  optional; password the sharees should use to access
   * the cart <CODE>replaceCartId</CODE>
   * @param url  optional; URL to access the shared cart
   * @param emailAddresses  optional; email addresses of the sharees
   * @param privilegeTypes  optional; privilege types of the sharees
   * @param comment  optional; comments to be sent in the email notification
   * to the persons who are being granted access to the cart.
   * @return cart ID of the replaced cart
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  // not used in iStore code as of IBE.P
  public static String replaceAndShare(String cartId,
                                       String lastUpdateTimestamp,
                                       String replaceCartId,
                                       String cartPassword, String url,
                                       String[] emailAddresses,
                                       String[] privilegeTypes,
                                       String comment) throws SQLException,
                                       FrameworkException, QuoteException,
                                       ShoppingCartException
  {

    String METHOD = "replaceAndShare";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "replaceCartId = " + replaceCartId);
    IBEUtil.log(CLASS, METHOD, "url = " + url);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "comment = " + comment);

}
    java.sql.Timestamp  updateTimestamp = null;

    if((lastUpdateTimestamp != null)
            && (!lastUpdateTimestamp.equals(EMPTY_STRING)))
    {
      updateTimestamp = java.sql.Timestamp.valueOf(lastUpdateTimestamp);

    }

    oracle.apps.ibe.shoppingcart.quote.ControlRecord  controlRecord =
      new oracle.apps.ibe.shoppingcart.quote.ControlRecord();

    ShoppingCartUtil.setupControlRecord(controlRecord, NO, NO);

    BigDecimal  newCartId = null;

    try
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.replaceAndShare");
}
      newCartId = Quote.replaceAndShare(new BigDecimal(cartId),
                                        RequestCtx.getPartyId(),
                                        RequestCtx.getAccountId(),
                                         RequestCtx.getShareeNumber(),
                                        null, updateTimestamp,
                                        new BigDecimal(replaceCartId), true,
                                        cartPassword, url, emailAddresses,
                                        privilegeTypes, comment,
                                        StoreMinisite.getPriceListID(),
                                        RequestCtx.getCurrencyCode(),
                                        true,
                                        controlRecord);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.replaceAndShare");
      IBEUtil.log(CLASS, METHOD, "New cart id = " + newCartId);
}
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "newCartId = " + newCartId);

}
    if(newCartId == null)
    {
      throw new ShoppingCartException("IBE_SC_UNABLE_TO_REPLACE");

    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");

}
    return newCartId.toString();

  }

  /**
   * Saves a cart and sharee information. The API makes a copy of
   * <CODE>cartId</CODE> and saves it with the passed <CODE>newCartName</CODE>.
   * <P>
   * The optional parameters, <CODE>cartPassword</CODE>, <CODE>url</CODE>,
   * <CODE>emailAddresses</CODE>, and <CODE>privilegeTypes</CODE>, are to share the
   * cart.
   * <P> Shipping and tax charges are calculated during this operation. <P>
   * @param cartId  ID of the cart being saved
   * @param lastUpdateTimestamp Optional.  The last update date of the cart
   * header. If it is not null, a check is done to verify that there was
   * no update to the cart header after the given last update date.
   * @param newCartName  name for the cart being saved
   * @param cartPassword  optional; password the sharees should use to access
   * the cart
   * @param url  optional; URL to access the shared cart
   * @param emailAddresses  optional; email addresses of the sharees
   * @param privilegeTypes  optional; privilege types of the sharees
   * @param comment  optional; comments to be sent in the email notification
   * to the persons who are being granted access to the cart.
   * @return cart ID of the saved cart
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  // not used in iStore code as of IBE.P   
  public static String saveAsAndShare(String cartId,
                                      String lastUpdateTimestamp,
                                      String newCartName,
                                      String cartPassword, String url,
                                      String[] emailAddresses,
                                      String[] privilegeTypes,
                                      String comment) throws SQLException,
                                      FrameworkException, QuoteException,
                                      ShoppingCartException
  {
    String METHOD = "saveAsAndShare";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "newCartName = " + newCartName);
    IBEUtil.log(CLASS, METHOD, "url = " + url);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "comment = " + comment);

}
    java.sql.Timestamp  updateTimestamp = null;

    String              userType = RequestCtx.userIsLoggedIn()
                                   ? ACCOUNT_USER_TYPE : WALKIN_USER_TYPE;

    if((lastUpdateTimestamp != null)
            && (!lastUpdateTimestamp.equals(EMPTY_STRING)))
    {
      updateTimestamp = java.sql.Timestamp.valueOf(lastUpdateTimestamp);

    }

    oracle.apps.ibe.shoppingcart.quote.ControlRecord  controlRecord =
      new oracle.apps.ibe.shoppingcart.quote.ControlRecord();

    ShoppingCartUtil.setupControlRecord(controlRecord, NO, NO);

    BigDecimal  newCartId = null;

    try
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.saveAsAndShare");
}
      newCartId = Quote.saveAsAndShare(new BigDecimal(cartId),
                                       updateTimestamp, newCartName,
                                       userType, RequestCtx.getPartyId(),
                                       RequestCtx.getAccountId(),
                                       RequestCtx.getShareeNumber(),
                                       null, cartPassword, url, 
                                       emailAddresses, privilegeTypes, 
                                       comment, StoreMinisite.getPriceListID(),
                                       RequestCtx.getCurrencyCode(),
                                   true, controlRecord);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.saveAsAndShare");
      IBEUtil.log(CLASS, METHOD, "New cart id = " + newCartId);
}
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    if(newCartId == null)
    {
      throw new ShoppingCartException("IBE_SC_UNABLE_TO_SAVE");

    }

    return newCartId.toString();

  }


  /**
   * Inactivates a cart.
   *
   *
   * @param cartNumber - the cart number (<B>Note: not the cart id </B>)
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
 // not used in iStore code as of IBE.P   
  public static void inactivateCart(String cartNumber)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {

    try
    {
      Quote.inactivate(new BigDecimal(cartNumber));
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
  }

  /**
   * Method to add services to items in a cart. Please see the
   * other signature of addItemServices for more details of the API. The
   * price is always recalculated by calling this method.
   * @param cartItems - the items in the cart for which services need to added.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  public void addItemServices(ShoppingCartItem[] cartItems)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    addOrUpdateItemServices(cartItems, CREATE_OPCODE, true);
  }

  /**
   * Method to update the services of the items in a cart. Please see the
   * other signature of updateItemServices for more details of the API. The
   * price is always recalculated by calling this method.
   * @param cartItems - the items for which the services need to be updated.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  public void updateItemServices(ShoppingCartItem[] cartItems)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    addOrUpdateItemServices(cartItems, UPDATE_OPCODE, true);
  }

  /**
   * Method to add services to items in a cart. The service items
   * need to be set in the cart items by calling the setServiceItems API of
   * ShoppingCartItem. Each cart item will need to have the cart line id, quantity,
   * inventory item id set. Each service item in the cart item will need to have
   * the order type id, uom code,
   * start date active, category code fields set by calling the appropriate
   * set method.
   * @param cartItems - the items for which the services need to be changed.
   * @param recalcPriceFlag - flag to indicate whether the price needs to be
   * recalculated after the services have been changed.
   *
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  public void addItemServices(ShoppingCartItem[] cartItems,
                              boolean recalcPriceFlag) throws SQLException,
                              FrameworkException, QuoteException,
                              ShoppingCartException
  {
    addOrUpdateItemServices(cartItems, CREATE_OPCODE, recalcPriceFlag);
  }

  /**
   * Method to update the services of the items in a cart. The service items
   * need to be set in the cart items by calling the setServiceItems API of
   * ShoppingCartItem. Each cart item will need to have the cart line id, quantity,
   * inventory item id set. Each service item in the cart item will need to have
   * the line detail record id, cart line id, order type id, uom code,
   * start date active, category code fields set by calling the appropriate
   * set method.
   * @param cartItems - the items for which the services need to be changed.
   * @param recalcPriceFlag - flag to indicate whether the price needs to be
   * recalculated after the services have been changed.
   *
   * @throws FrameworkException - if there is a framework layer error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   * @throws SQLException - if there is a database error
   */
  public void updateItemServices(ShoppingCartItem[] cartItems,
                                 boolean recalcPriceFlag) throws SQLException,
                                 FrameworkException, QuoteException,
                                 ShoppingCartException
  {
    addOrUpdateItemServices(cartItems, UPDATE_OPCODE, recalcPriceFlag);
  }

  /**
   * Adds or updates the services of items in the cart. The
   * operation code will indicate whether it is a addition or an update. Please
   * see the addItemServices or updateItemServices APIs for more details.
   *
   * @param cartItems The items for which the services need to be changed.
   * @param operationCode The operation code.
   * @param recalcPriceFlag The flag to indicate whether the price needs to be
   * recalculated after the services have been changed.
   *
   * @throws FrameworkException If there is a framework layer error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of the error
   * @throws SQLException If there is a database error
   * @rep:displayname Add or Update Item Service
   */
  public void addOrUpdateItemServices(ShoppingCartItem[] cartItems,
                                      String operationCode,
                                      boolean recalcPriceFlag) throws SQLException,
                                      FrameworkException, QuoteException,
                                      ShoppingCartException
  {

    String  METHOD = "addOrUpdateItemServices";
    NumFormat numFormat = new NumFormat(RequestCtx.getLanguageCode());
    if(IBEUtil.logEnabled()) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "operationCode = " + operationCode);
    IBEUtil.log(CLASS, METHOD, "recalcPriceFlag = " + recalcPriceFlag);
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    boolean isCreate = false;

    if(operationCode.equals(CREATE_OPCODE))
    {
      isCreate = true;

    }

    if(cartItems != null)
    {
      int numCartItems = cartItems.length;

      if(numCartItems > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "num of items being updated = " + numCartItems);
}
        int numRecords = 0;

        for(int i = 0; i < numCartItems; i++)
        {
          if(cartItems[i] != null)
          {
            ServiceItem[] svcItems = cartItems[i].getServiceItems();


            if((svcItems != null) && (svcItems.length > 0))
            {
              numRecords += svcItems.length;
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                "For the " + i + " th item, num svc items are " + svcItems.length);
}
            }
          }
        }

        if(lineDetRec == null)
        {
          lineDetRec = new LineDetailRecord[numRecords];
        }

        if(lineRec == null)
        {
          lineRec = new LineRecord[numRecords];

          /*
           * if ( (isCreate) && (lineRelRec == null))
           * lineRelRec = new LineRelationshipRecord[numRecords];
           */

        }

        int         recordIndex = 0;
        BigDecimal  svaLineId = null;
        BigDecimal  quoteLineIdOrIndex = null;
        BigDecimal  quoteLineId = null;

        try
        {
          for(int i = 0; i < numCartItems; i++)
          {
            if(cartItems[i] != null)
            {
              BigDecimal    quoteHeaderId = new BigDecimal(cartId);
              ServiceItem[] svcItems = cartItems[i].getServiceItems();

              if(svcItems != null)
              {
                int svcItemsLength = svcItems.length;

                if(svcItemsLength > 0)
                {
                  for(int j = 0; j < svcItemsLength; j++)
                  {
                    ServiceItem svcItem = cartItems[i].svcItems[j];

                    if(isCreate)
                    {
                      svaLineId = new BigDecimal(cartItems[i].cartLineId);
                      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "SVA line id is " + svaLineId);
}
                      quoteLineIdOrIndex =
                        new BigDecimal((double) (recordIndex + 1));
                    }
                    else
                    {
                      quoteLineIdOrIndex =
                        new BigDecimal(svcItem.detailRecordId);
                      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Dtl rcrd id is " + svcItem.detailRecordId);
}
                      quoteLineId = new BigDecimal(svcItem.cartLineId);
                      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Service line id is " + svcItem.cartLineId);
}
                    }

                    if(lineDetRec[recordIndex] == null)
                    {
                      lineDetRec[recordIndex] =
                        new oracle.apps.ibe.shoppingcart.quote.LineDetailRecord();
                    }

                    setupLineDetailRecord(lineDetRec[recordIndex], svcItem,
                                          operationCode, quoteLineIdOrIndex,
                                          null, svaLineId);

                    if(lineRec[recordIndex] == null)
                    {
                      lineRec[recordIndex] =
                        new oracle.apps.ibe.shoppingcart.quote.LineRecord();
                    }

                    BigDecimal  bOrderTypeId = gMissNum;

                    if(!svcItem.orderTypeId.equals(EMPTY_STRING))
                    {
                      bOrderTypeId = new BigDecimal(svcItem.orderTypeId);
                      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "order type id is " + svcItem.orderTypeId);
}
                    }

                    if(isCreate)
                    {
                      ShoppingCartUtil.setupLineRecord(lineRec[recordIndex],
                                                       operationCode,
                                                       quoteHeaderId,
                                                       quoteLineId,
                                                       numFormat.parseNumber(cartItems[i].quantity),
                                                       new BigDecimal(svcItem.inventoryItemId),
                                                       new BigDecimal(svcItem.organizationId),
                                                       svcItem.uom,
                                                       ShoppingCartItem.SERVICE_ITEM_TYPE,
                                                       svcItem.startDateActive,
                                                       svcItem.categoryCode,
                                                       bOrderTypeId);

                      /*
                       * lineRelRec[recordIndex] =
                       * new oracle.apps.ibe.shoppingcart.quote.LineRelationshipRecord();
                       * setupLineRelationshipRecord( lineRelRec[recordIndex],
                       * operationCode, quoteLineIdOrIndex, svaLineId, "SERVICE");
                       */

                    }
                    else
                    {
                      BigDecimal  updQty =
                        (cartItems[i].quantity.equals("")) ? gMissNum
                        : numFormat.parseNumber(cartItems[i].quantity);
                      String      catgCode =
                        (svcItem.categoryCode.equals(EMPTY_STRING))
                        ? gMissChar : svcItem.categoryCode;

                      ShoppingCartUtil.setupLineRecord(lineRec[recordIndex],
                                                       operationCode,
                                                       quoteHeaderId,
                                                       quoteLineId, updQty,
                                                       new BigDecimal(svcItem.inventoryItemId),
                                                       new BigDecimal(svcItem.organizationId),
                                                       svcItem.uom,
                                                       ShoppingCartItem.SERVICE_ITEM_TYPE,
                                                       svcItem.startDateActive,
                                                       catgCode,
                                                       bOrderTypeId);
                    }

                    recordIndex++;
                  }
                }
              }
            }
          }
        }
        catch(NumberFormatException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }
        catch(StringIndexOutOfBoundsException e)
        {
          throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
        }

        if(logEnabled)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "linerecord");

}
          if(lineRec != null && lineRec.length > 0)
          {
            int lineRecLength = lineRec.length;

            for(int i = 0; i < lineRecLength; i++)
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].quote_header_id is "
                          + lineRec[i].quote_header_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].inventory_item_id is "
                          + lineRec[i].inventory_item_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].organization_id is "
                          + lineRec[i].organization_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].item_type_code is "
                          + lineRec[i].item_type_code);
             IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].uom_code is " + lineRec[i].uom_code);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].quantity is " + lineRec[i].quantity);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].start_date_active is "
                          + lineRec[i].start_date_active);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].line_category_code is "
                          + lineRec[i].line_category_code);
             IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].order_line_type_id is "
                          + lineRec[i].order_line_type_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineRec[i].operation_code is "
                          + lineRec[i].operation_code);
                }
            }
          }

          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "line detailrecord");

}
          if(lineDetRec != null && lineDetRec.length > 0)
          {
            int lineDetRecLength = lineDetRec.length;

            for(int i = 0; i < lineDetRecLength; i++)
            {
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].quote_line_detail_id is "
                          + lineDetRec[i].quote_line_detail_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].qte_line_index is "
                          + lineDetRec[i].qte_line_index);
}
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].service_ref_type_code is "
                          + lineDetRec[i].service_ref_type_code);
              IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].service_ref_qte_line_index is "
                          + lineDetRec[i].service_ref_qte_line_index);
}
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].service_ref_line_id is "
                          + lineDetRec[i].service_ref_line_id);
              IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].service_duration is "
                          + lineDetRec[i].service_duration);
}
              if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].service_period is "
                          + lineDetRec[i].service_period);
              IBEUtil.log(CLASS, METHOD,
                          " lineDetRec[i].operation_code is "
                          + lineDetRec[i].operation_code);
}
            }
          }
        }

        String  calcTax = (recalcPriceFlag) ? YES : NO;

        setupControlRecord(calcTax, NO, recalcPriceFlag);

        setupHeaderRecord(new BigDecimal(cartId));

        if(isCreate)
        {
          forceTimestampValidation();

        }

        boolean     saveRelRecords = false;

        /*
         * if (isCreate)
         * save(RequestCtx.getPartyId(), RequestCtx.getAccountId(),
         * RequestCtx.getShareeNumber(), Quote.SEPARATE_LINES, false,
         * true, true, false, false, false, false, false, false, false,
         * false, false, false, false, false, false, true, false);
         * else
         */

        BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
        BigDecimal  partyId = null;
        BigDecimal  acctId = null;

        if(shareeNumber != null)
        {
          partyId = RequestCtx.getPartyId();
          acctId = RequestCtx.getAccountId();
        }

        try
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Abt to call quote.save");
}
          save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
               true, true, false, false, false, false, false, false, false,
               false, false, false, false, false, false, false, false);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling quote.save");
}
        }
        catch(QuoteException e)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
          checkUpdateTimestamp(e);
        }

      }
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Method declaration
   *
   *
   * @param calcTaxFlag
   * @param calcFrieghtFlag
   *
   * @see
   */
  private void setupControlRecord(String calcTaxFlag, String calcFrieghtFlag,
                                  boolean priceRecalcFlag)
  {
    String METHOD = "setupControlRecord";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "calcTaxFlag is " + calcTaxFlag);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "calcFrieghtFlag is " + calcFrieghtFlag);
    IBEUtil.log(CLASS, METHOD, "priceRecalcFlag is " + priceRecalcFlag);
}
    if(controlRec == null)
    {
      controlRec = new oracle.apps.ibe.shoppingcart.quote.ControlRecord();
    }

    if(!priceRecalcFlag)
    {
      controlRec.pricing_request_type = null;
      controlRec.header_pricing_event = null;
      controlRec.line_pricing_event = null;
    }

    ShoppingCartUtil.setupControlRecord(controlRec, calcTaxFlag,
                                        calcFrieghtFlag);

  }

  /** Calls the other signature of repriceCart with a null value for the lastUpdateDate */
  public static void repriceCart(String cartId, String currencyCode)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    ShoppingCart.repriceCart(cartId, currencyCode, null);
  }
  /**
   * Reprices the cart with the specified currency code.
   * Sets the headerRec, controlRec and calls Quote.save.
   * After a successful call, sets the cart total in the cookie with the new amount.
   *
   * @param cartId The unique identifier of the cart to be repriced.
   * @param currencyCode The currency code used to reprice the cart.
   * @param lastUpdateDate The last update timestamp of the cart to be repriced.
   *
   * @throws FrameworkException If there is a framework layer error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of the error
   * @throws SQLException If there is a database error
   * @rep:displayname Reprice Cart
   */
  public static void repriceCart(String cartId, String currencyCode, Timestamp lastUpdateDate)
          throws SQLException, FrameworkException, QuoteException,
                 ShoppingCartException
  {
    String  METHOD = "repriceCart";
    boolean logEnabled = IBEUtil.logEnabled();

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "currencyCode = " + currencyCode);
    IBEUtil.log(CLASS, METHOD, "lastUpdateDate = " + lastUpdateDate);
}
    try
    {
      BigDecimal    quoteHeaderId = new BigDecimal(cartId);

      ShoppingCart  shopCart = new ShoppingCart();

      shopCart.headerRec = new HeaderRecord();
      shopCart.headerRec.currency_code = currencyCode;
      shopCart.headerRec.last_update_date = lastUpdateDate;

      shopCart.setupHeaderRecord(quoteHeaderId);
      shopCart.setupControlRecord(YES, YES, true);

      BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
      BigDecimal  partyId = null;
      BigDecimal  acctId = null;

      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shareeNumber = " + shareeNumber);
}
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }

      try
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "partyId = " + partyId);
        IBEUtil.log(CLASS, METHOD, "acctId = " + acctId);
}
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.save");
}
        shopCart.save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, 
                      false, false, false, false, false, false, false, false, 
                      false, false, false, false, false, false, false, false, 
                      false, false);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Quote.save");
}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        shopCart.checkUpdateTimestamp(e);
      }
      // new condition - only set cart total if it's the active cart we're repricing
      if (quoteHeaderId.equals(RequestCtx.getCartId())) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling setCartTotal");
}
        ShoppingCartUtil.setCartTotal(cartId);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling setCartTotal");
}

        // added 12/11/03: Conc Issue: addToCart
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting lastModifiedTimestamp: "+shopCart.lastModifiedTimestamp); }
        RequestCtx.setCartLastUpdateDate(shopCart.lastModifiedTimestamp);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting lastModifiedTimestamp: done"); }

      }
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
  }

  /**
   * Method declaration
   *
   *
   * @param e
   *
   * @throws QuoteException
   * @throws ShoppingCartException
   *
   * @see
   */
  private void checkUpdateTimestamp(QuoteException e)
          throws QuoteException, ShoppingCartException
  {
    String  METHOD = "checkUpdateTimestamp";

    // boolean isSalesRep = RequestCtx.userIsSalesRep()? true: false;
//    boolean isSharee = (RequestCtx.getShareeNumber() != null) ? true : false;

    if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                "TS from jsp page is : " + lastModifiedTimestamp);

}
    //if((isSharee) && (lastModifiedTimestamp != null)
    if((lastModifiedTimestamp != null)
            && (!lastModifiedTimestamp.equals(EMPTY_STRING))
            && (headerRec.last_update_date != null)
            && (headerRec.last_update_date != gMissDate))
    {
      if(logEnabled) { //IBEUtil.log(CLASS, METHOD, "Yes, is a sharee");
      IBEUtil.log(CLASS, METHOD,
                  "TS from DB: " + headerRec.last_update_date.toString());
}

      java.sql.Timestamp  lastModTS =
        java.sql.Timestamp.valueOf(lastModifiedTimestamp);

      if(headerRec.last_update_date.after(lastModTS))
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Timestamp in DB is later than web tier");
}
        reloadFlag = true;

        /*
         * if (isSalesRep)
         * throw new ShoppingCartException("IBE_SC_QTE_OUT_OF_SYNC");
         * else
         */
        String errMsg = IBEUtil.getMessages(e);
        if ((errMsg == null) || "".equals(errMsg)) {
          if(logEnabled) IBEUtil.log(CLASS, METHOD, "throwing IBE_SC_SH_CART_OUT_OF_SYNC");
          throw new ShoppingCartException("IBE_SC_SH_CART_OUT_OF_SYNC");
        } else {
          if(logEnabled) IBEUtil.log(CLASS, METHOD, "throwing error msg from original exception: " + errMsg);
          throw new ShoppingCartException(errMsg);
        }
      }
      else
      {
        throw e;
      }
    }
    else
    {
      throw e;
    }
  }

  /**
   * Method declaration
   *
   *
   * @see
   */
  private void forceTimestampValidation()
  {

    if(headerRec == null)
    {
      headerRec = new oracle.apps.ibe.shoppingcart.quote.HeaderRecord();

    }

    if((lastModifiedTimestamp != null) && (!lastModifiedTimestamp.equals("")))
    {
      headerRec.last_update_date = Timestamp.valueOf(lastModifiedTimestamp);
    }
  }

  /**
   * Merges a guest cart with the user's active cart (account cart).<BR>
   * <BR>
   * This api has three main purposes:<BR>
   * 1. To try and merge/keep the items in the user's guest active cart.<BR>
   * 2. To determine/retrieve the now, signed-in user's active cart.<BR>
   * 3. To set the cookie appropriately.<BR>
   * <BR>
   * There are several different cases:
   * (guest cart is represented by the cartId input parameter, account active cart (if present) is marked in the database)
   * <LI>case 1: no guest cart, no account active cart - no active cart, api returns null
   * <LI>case 2: guest cart is input, no account active cart - guest cart gets "promoted" (updated to be owned by the account user, checkout info gets defaults where possible, & repriced) and marked as the account active cart, api returns cartid
   * <LI>case 3: no guest cart, account active cart is present - account active cart is repriced, api returns cartid
   * <LI>case 4: guest cart is input, account active cart is present - depends on the mode passed to Quote.merge (please see comments for Quote.merge), one way or another, a cartid is returned
   * <LI>case 5: guest cart is input, account active cart is present but shared - guest cart gets "promoted" and marked as account active cart, shared active cart gets deactivated, api returns promoted cartid
   * <LI>case 6: no guest cart is input, account active cart is present but shared - api returns shared active cartid and retrieval number
   * <BR><BR>
   * Once the user's active cart is determined, the method seeks to set the cookie appropriately.
   * <LI>If there is an active cart, it will set the cartId and the cartTotal.
   * <LI>If not, it will clear the cart total.
   * <LI>If the active cart is a shared cart, it will set the sharee number.
   * <BR><BR>
   * This api internally calls Quote.merge with the following parameters:
   * <LI>the cartId passed to this api
   * <LI>mode is "MERGE" unless the Support feature is off and the Cart Level support is turned on in which case the mode is "KEEP"
   * <LI>combineSameItem is set to USE_PROFILE
   * <LI>cookie values for party id, account id, and currency code
   * <LI>minisite price list id for price list id
   * <LI>controlRec with settings to reprice
   *
   * @param cartId - the guest cart id
   * @param shareeNumber - the number of the person sharing the cart. Set to null
   * if the user owns the cart.  If a nonnull value is passed in, the api returns null,
   * the cartId (if passed) will get set in the cookie, and if the userWasAnonymous
   * is true, the cartId gets repriced.
   * @param originalOrgId - the original org id - if this does not match the
   * current org id (from the cookie), the cartId parameter will be ignored (as carts cannot move across orgs).
   * @param userWasAnonymous - flag to indicate if the user was a guest user before signing in.
   * If false, the cartId parameter will be ignored. If true, and there is a shareeNumber passed in, the cartId specified will be repriced.
   * @param registrationEvent - flag to indicate if this merge is being called
   * immediately after the guest has registered.  If this is true and cartId is null,
   * there is no reason to hit the db (since the newly registered user will not have
   * an active cart) and this method returns null.
   *
   * @return  the id of the merged cart
   *
   * @throws FrameworkException  if there is a framework layer error
   * @throws QuoteException  the error message will indicate the nature of the error
   * @throws ShoppingCartException  the error message will indicate the nature of the error
   * @throws SQLException -if there is a database error
   *
   */
  public static String mergeCart(String cartId, String shareeNumber,
                                 String originalOrgId,
                                 boolean userWasAnonymous,
                                 boolean registrationEvent) throws SQLException,
                                 FrameworkException, QuoteException,
                                 ShoppingCartException
  {
    String  mergedCartId = null;
    String  METHOD = "mergeCart";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
    IBEUtil.log(CLASS, METHOD, "shareeNumber = " + shareeNumber);
    IBEUtil.log(CLASS, METHOD, "originalOrgId = " + originalOrgId);
    IBEUtil.log(CLASS, METHOD, "userWasAnonymous = " + userWasAnonymous);
     IBEUtil.log(CLASS, METHOD, "registrationEvent = " + registrationEvent);

}
    if((registrationEvent) && (cartId == null))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Cart id null or registration event,no merge rqrd");
      IBEUtil.log(CLASS, METHOD, "DONE");
}
      return null;

    }

    String  currOrgId = RequestCtx.getCookieValue(RequestCtx.ORG_ID);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "currOrgId = " + currOrgId);

}
    if(originalOrgId != null &&!originalOrgId.equals(currOrgId))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "no merge: originalOrgId=" + originalOrgId + ", newOrgId="
                  + RequestCtx.getCookieValue(RequestCtx.ORG_ID));

}
      cartId = null;
    }

    if(!userWasAnonymous)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "no merge: orig user is not anonymous");

}
      cartId = null;
    }

    if(shareeNumber != null)
    {
      RequestCtx.setCookieValue(RequestCtx.SHAREE_NUMBER, shareeNumber);

      if(cartId != null)
      {
        RequestCtx.setCookieValue(RequestCtx.CART_ID, cartId);
      }
      // added 11/15/01: if sharee, G->Acct, login -> reprice cart
      if(userWasAnonymous)
      {
        repriceCart(cartId, RequestCtx.getCurrencyCode());
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
      return null;
    }

    // ////////////////////////////////////////////////////////////////////////
    if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                "calling Quote.merge: " + cartId + ":"
                + RequestCtx.getCookieValue(RequestCtx.PARTY_ID) + ":"
                + RequestCtx.getCookieValue(RequestCtx.ACCOUNT_ID));

}
    String  mode = "MERGE";

    if(!IBEUtil.useFeature("IBE_USE_SUPPORT")
            && IBEUtil.useFeature("IBE_USE_SUPPORT_CART_LEVEL"))
    {
      mode = "KEEP";
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
        "Cart level support feature is enabled, merge mode will be KEEP");
}
    }

    BigDecimal cartIdBD = (cartId == null) ? null : new BigDecimal(cartId);

    oracle.apps.ibe.shoppingcart.quote.ControlRecord  controlRec =
      new oracle.apps.ibe.shoppingcart.quote.ControlRecord();

    controlRec.pricing_request_type = ShoppingCartUtil.PRICE_REQUEST_TYPE;
    controlRec.header_pricing_event =
      StoreMinisite.getMinisiteAttribute("IBE_INCART_PRICING_EVENT");
    controlRec.line_pricing_event = ShoppingCartUtil.gMissChar;
    controlRec.calculate_tax_flag = "Y";
    controlRec.calculate_freight_charge_flag = "Y";

    Quote  newQuote = Quote.merge(cartIdBD, null, mode,
                                        Quote.USE_PROFILE,
                                        RequestCtx.getPartyId(),
                                        RequestCtx.getAccountId(),
                                        StoreMinisite.getPriceListID(),
                                        RequestCtx.getCurrencyCode(),
                                        controlRec, null);
    if (newQuote == null) {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Quote.merge returned null");
}
      return "";
    }
    // deal with the new merged Quote header id
    if((newQuote.headerRec != null) && (newQuote.headerRec.quote_header_id != null))
    {
      mergedCartId = newQuote.headerRec.quote_header_id.toString();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  "after Quote.merge: newCartId = " + mergedCartId);

}
      RequestCtx.setCookieValue(RequestCtx.CART_ID, mergedCartId);
      // we don't need to have any conditions around this call since merge will always be
      // dealing with the active cart
      ShoppingCartUtil.setCartTotal(mergedCartId);

      if(newQuote.headerRec.last_update_date != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Setting lastModifiedTimestamp: "+newQuote.headerRec.last_update_date.toString()); }
        RequestCtx.setCartLastUpdateDate(newQuote.headerRec.last_update_date.toString() );
      }

    }
    else
    {
      // we don't need to have any conditions around this call since merge will always be
      // dealing with the active cart
      RequestCtx.setCartTotal(PriceObject.formatNumber(RequestCtx.getCurrencyCode(),
              (double) 0));
    }
    // deal with a possible retrieval number (if the cart is a shared cart)
    if ((newQuote.quoteAccessRec != null) && (newQuote.quoteAccessRec.length > 0)
        && (newQuote.quoteAccessRec[0] != null) && (newQuote.quoteAccessRec[0].quote_sharee_number != null))
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after Quote.merge: retrievalNumber = " + newQuote.quoteAccessRec[0].quote_sharee_number);
}
      RequestCtx.setCookieValue(RequestCtx.SHAREE_NUMBER, newQuote.quoteAccessRec[0].quote_sharee_number.toString());
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "mergedCartId = " + mergedCartId);
    IBEUtil.log(CLASS, METHOD, "DONE");
}
    return mergedCartId;
  }


  /**
   * Method declaration
   * (formerly loadLineTaxInfo)
   *
   * @throws FrameworkException
   * @throws SQLException
   *
   * @see
   */
  private void fillAllLinesTaxInfo() throws FrameworkException, SQLException
  {
    String  METHOD = "fillAllLinesTaxInfo";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    }
    int numArrays = 0;

    if(shopCartItems != null) {
      numArrays++;
    }
    if(shopCartPRGItems != null && loadControlRec.loadPRGItems) {
      numArrays++;
    }
    HashMap lineLevelTaxObjects = null;
    if (numArrays > 0) {
      lineLevelTaxObjects = TaxInfo.getLineTaxInfo(cartId,currencyCode);
    }

    ShoppingCartItem[] currentItemsArray = null;
    for (int k = 0; k < numArrays; k++)
    {
      if (k == 0) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for nonPRG array");
        }
        currentItemsArray = shopCartItems;
      } else if (k == 1) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for PRG array");
        }
        currentItemsArray = shopCartPRGItems;
      }
      int numCartItems = currentItemsArray.length;

      if(numCartItems > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems is " + numCartItems);

}
        for(int i = 0; i < numCartItems; i++)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                      " cartLineId is " + currentItemsArray[i].cartLineId);
}
          ShoppingCartUtil.populateLineTaxInfo(lineLevelTaxObjects,
                                               currentItemsArray[i]);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Done getting line tax info");
}
          ServiceItem[] svcItems = currentItemsArray[i].getServiceItems();

          if(svcItems != null)
          {
            int svcItemsLength = svcItems.length;

            if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                        "Number of services for SVA is " + svcItemsLength);

}
            for(int j = 0; j < svcItemsLength; j++)
            {
              if(svcItems[j] != null)
              {
                if(logEnabled) { IBEUtil.log(CLASS, METHOD, "calling pop line tax info");
}
                ShoppingCartUtil.populateLineTaxInfo(lineLevelTaxObjects,
                                                     svcItems[j]);
              }
            }   // for loop over service items
          }     // if (svcItems != null)
        }       // for loop over cart items
      }         // if numCartItems > 0
    }           // for loop over count of arrays
  }

  // counts on previous fill operations to have set the commitment id into the ShoppingCartItem objects  
  private void fillAllLinesCommitmentInfo() throws FrameworkException, SQLException
  {
    String  METHOD = "fillAllLinesCommitmentInfo";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
    int numArrays = 0;

    if(shopCartItems != null) {
      numArrays++;
    }
    if(shopCartPRGItems != null && loadControlRec.loadPRGItems) {
      numArrays++;
    }
    HashMap commitmentHash = null;
    if (numArrays > 0) {
      commitmentHash = ShoppingCartUtil.loadCommitmentInfo(cartId);
    }
    ShoppingCartItem[] currentItemsArray = null;
    for (int k = 0; k < numArrays; k++)
    {
      if (k == 0) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for nonPRG array");
        }
        currentItemsArray = shopCartItems;
      } else if (k == 1) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for PRG array");
        }
        currentItemsArray = shopCartPRGItems;
      }
      int numCartItems = currentItemsArray.length;

      if(numCartItems > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems is " + numCartItems);

}
        String currCommitmentId = EMPTY_STRING;
        for(int i = 0; i < numCartItems; i++)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " cartLineId is " + currentItemsArray[i].cartLineId);
}
          currCommitmentId = currentItemsArray[i].getCommitmentId();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " cartLine commitmentId is " + currCommitmentId);
}
          if (!EMPTY_STRING.equals(currCommitmentId)) {
            Commitment currCommitment = (Commitment) commitmentHash.get((String) currCommitmentId);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, " currCommitment is " + currCommitment);
}
            currentItemsArray[i].setCommitmentInfo(currCommitment);
          } else {
            currentItemsArray[i].setCommitmentInfo(new Commitment());
          }
        }
      }
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "END");   
}
  }

  // counts on previous fill operations to have set the agreement id into the ShoppingCartItem objects
  private void fillAllLinesAgreementInfo() throws FrameworkException, SQLException
  {
    String  METHOD = "fillAllLinesAgreementInfo";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    int numArrays = 0;

    if(shopCartItems != null) {
      numArrays++;
    }
    if(shopCartPRGItems != null && loadControlRec.loadPRGItems) {
      numArrays++;
    }
    HashMap agreementHash = null;
    if (numArrays > 0) {
      agreementHash = ShoppingCartUtil.loadLineAgreementInfo(cartId);
    }
    ShoppingCartItem[] currentItemsArray = null;
    for (int k = 0; k < numArrays; k++)
    {
      if (k == 0) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for nonPRG array");
        }
        currentItemsArray = shopCartItems;
      } else if (k == 1) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " filling line tax info for PRG array");
        }
        currentItemsArray = shopCartPRGItems;
      }
      int numCartItems = currentItemsArray.length;

      if(numCartItems > 0)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems is " + numCartItems);

}
        String currAgreementId = EMPTY_STRING;
        for(int i = 0; i < numCartItems; i++)
        {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " cartLineId is " + currentItemsArray[i].cartLineId);
}
          currAgreementId = currentItemsArray[i].getAgreementId();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " cartLine agreementId is " + currAgreementId);
}
          if (!EMPTY_STRING.equals(currAgreementId)) {
            Agreement currAgreement = (Agreement) agreementHash.get((String) currAgreementId);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, " currAgreement is " + currAgreement);
}
            currentItemsArray[i].setAgreementInfo(currAgreement);
          } else {
            currentItemsArray[i].setAgreementInfo(new Agreement());
          }
        }
      }
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "END");
}
  }

  /**
   * Adds one or more model items to a cart.The method expects the following to be set:
   * <LI> this.cartId (optional).
   * <LI> this.shopCartItems.inventoryItemId.
   * <LI> this.shopCartItems.quantity.
   * <LI> this.shopCartItems.uom.
   * 
   * @param calcTaxFlag If <CODE>YES</CODE>, calculates tax. If <CODE>NO</CODE>, does not.
   * @param calcFrieghtFlag If <CODE>YES</CODE>, calculates shipping costs. If <CODE>NO</CODE>, does not.
   * @param priceRecalcFlag The flag to drive repricing of the cart
   * @param combineSameItem  If <CODE>COMBINED_LINES</CODE>, it combines lines of
   * the same item.  If <CODE>SEPARATE_LINES</CODE>, it creates seperate lines for
   * the same item.  If <CODE>USE_PROFILE</CODE> or any other values, it uses the
   * combines lines or create seperate lines.  This parameter does not affect
   * configurable and service items.
   * @param bundleFlag - This flag is no longer used.  To trigger bundle logic, the item must have the line code set to ShoppingCartItem.MODEL_BUNDLE_LINE_CODE.
   * @throws SQLException If there is a database error
   * @throws FrameworkException If there is a framework layer error
   * @throws ShoppingCartException The error message will indicate the nature of the error
   * @rep:displayname Add Models to Cart
   */
  public void addModelsToCart(
                   String calcTaxFlag,
                   String calcFrieghtFlag,
                   boolean priceRecalcFlag,
                   int        combineSameItem,
                   boolean    bundleFlag)
  throws SQLException, FrameworkException, ShoppingCartException
  {
    long startTime = System.currentTimeMillis();

    String  METHOD = "addModelsToCart";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId        = " + cartId);
    IBEUtil.log(CLASS, METHOD, "combineSameItem = " + combineSameItem);
    IBEUtil.log(CLASS, METHOD, "bundleFlag      = " + bundleFlag);
}
//-------------- Setup MinisiteId --------------------------------------------//
  if((cartId == null || EMPTY_STRING.equals(cartId)) && 
       (headerRec == null || (headerRec != null && (headerRec.minisite_id == null || gMissNum.equals(headerRec.minisite_id)))))
  {
    if(headerRec == null)
    {
      headerRec = new HeaderRecord();
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Minisite_Id is set for the new quote: "+RequestCtx.getMinisiteId());
}
    headerRec.minisite_id = RequestCtx.getMinisiteId();
  }
//--------------  setup control & header records -----------------------------//
    setupControlHeaderLineRecords(calcTaxFlag,calcFrieghtFlag,priceRecalcFlag);

//----------------------  prepare & execute jdbc call ------------------------//
    BigDecimal shareeNumber = RequestCtx.getShareeNumber();
    BigDecimal shareePartyID = null;
    BigDecimal shareeCustAcctID = null;
    if (shareeNumber != null) {
      shareePartyID = RequestCtx.getPartyId();
      shareeCustAcctID = RequestCtx.getAccountId();
    }

    String[] x_return_status = new String[1];
    int[]    x_msg_count     = new int[1];
    String[] x_msg_data      = new String[1];
    x_msg_count[0] = 0;

    BigDecimal[] x_quote_header_id = new BigDecimal[1];
    Timestamp[]  x_last_update_date = new Timestamp[1];
    BigDecimal[] x_ql_quote_line_id;
    int i = 0;
    int j = 0;
    int k = 0;

    OracleConnection conn = (OracleConnection) TransactionScope.getConnection();
    conn.setAutoCommit(false);
    StringBuffer ocsStmt = new StringBuffer(1000);
//    ocsStmt = new StringBuffer(1000);
    // since out parameters that are arrays can only be set by location
    // as opposed to being set explicitly (with name =>...), we have the
    // line id out array as the 1st procedure arguement.
    ocsStmt.append("BEGIN " + IBEUtil.getEnableDebugString() + " IBE_Quote_W1_pvt.AddModelsToCartWrapper(:1, " +
                      "x_return_status    => :2, " +
                      "x_msg_count        => :3, " +
                      "x_msg_data         => :4, " +
                      "x_quote_header_id  => :5, " +
                      "x_last_update_date => :6, ");

    if (bundleFlag)
      ocsStmt.append("p_Bundle_Flag => FND_API.G_TRUE,");
      // else let default be FND_API.G_FALSE
    i = 7;
    if (shareeNumber != null)
      ocsStmt.append("p_sharee_number => :" + i++ + ",");
    if (shareePartyID != null)
      ocsStmt.append("p_sharee_party_id => :" + i++ + ",");
    if (shareeCustAcctID != null)
      ocsStmt.append("p_sharee_cust_account_id => :" + i++ + ",");

    // helper functions that add respective parameters to the stmt string buffer
    int[] n = new int[1];
    n[0]    = i;
    setOcsStmtControlRec(ocsStmt,n);
    setOcsStmtHeaderRec(ocsStmt,n);
    setOcsStmtLineRec(ocsStmt, n);
//    i = n[0] // if we ever need the counter after these

    if (combineSameItem == COMBINED_LINES)
      ocsStmt.append("p_combinesameitem => 'Y'); " + IBEUtil.getDisableDebugString() + " END;");
    else if (combineSameItem == SEPARATE_LINES)
      ocsStmt.append("p_combinesameitem => 'N'); " + IBEUtil.getDisableDebugString() + " END;");
    else
      ocsStmt.append("p_combinesameitem => FND_API.gMissChar); " + IBEUtil.getDisableDebugString() + " END;");

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Callable Statment: " + ocsStmt.toString());
    IBEUtil.log(CLASS, METHOD, "before prepare call");
}
    OracleCallableStatement ocs = (OracleCallableStatement) conn.prepareCall(ocsStmt.toString());
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after prepare call");

}
    // register types of OUT and IN-OUT, in any
    ocs.registerOutParameter(1, OracleTypes.ARRAY, "JTF_NUMBER_TABLE");
    ocs.registerOutParameter(2, OracleTypes.VARCHAR,0,IBEUtil.RTN_STATUS_MAXLENGTH);
    ocs.registerOutParameter(3, OracleTypes.NUMBER);
    ocs.registerOutParameter(4, OracleTypes.VARCHAR,0,IBEUtil.MSGDATA_MAXLENGTH);
    ocs.registerOutParameter(5, OracleTypes.NUMBER);
    ocs.registerOutParameter(6, OracleTypes.TIMESTAMP);

    // register IN or IN-OUT params, if any
    i = 7;
    if (shareeNumber != null)
      ocs.setBigDecimal(i++, shareeNumber);
    if (shareePartyID != null)
      ocs.setBigDecimal(i++, shareePartyID);
    if (shareeCustAcctID != null)
      ocs.setBigDecimal(i++, shareeCustAcctID);

    // helper functions that set types for respective record types
    int[] s = new int[1];
    s[0]    = i;
    setControlRec(ocs, s);
    setHeaderRec(ocs, s);
    if (lineRec != null && lineRec.length > 0)
    {
      setLineRec(lineRec, ocs, conn, s);
      i = s[0];
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Header Minisite_id "+headerRec.minisite_id);

}
    try
    {
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "before excecute");
}
    ocs.execute();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute");

}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "x_return_status[0]: " + x_return_status[0]);
    IBEUtil.log(CLASS, METHOD, "x_msg_count[0]: " + x_msg_count[0]);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "x_msg_data[0]: " +x_msg_data[0]);

}
      // get OUT and IN-OUT params, if any
      ARRAY lineIds = (ARRAY)(ocs.getObject(1));
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute0");
}
      x_ql_quote_line_id = (BigDecimal[]) lineIds.getArray();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute1");
}
      x_return_status[0] = ocs.getString(2);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute2");
}
      x_msg_count[0]     = ocs.getInt(3);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute3");
}
      x_msg_data[0]      = ocs.getString(4);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute4");
}
      x_quote_header_id[0]  = ocs.getBigDecimal(5);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute5");
}
      x_last_update_date[0] = ocs.getTimestamp(6);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "after excecute6");

}
      if (!FndConstant.getGRetStsSuccess().equals(x_return_status[0]))
      {
        if (x_msg_count[0] > 1)
        {
          if (FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
            throw ErrorStackUtil.getDBFrameworkException(x_msg_count[0]);
          else
            throw new ShoppingCartException(x_msg_count[0], (String)null);
        }
        else
        {
          if (FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
            throw new FrameworkException(0, x_msg_data[0]);
          else
            throw new ShoppingCartException(x_msg_data[0], (String)null);
        }
      }
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "headerid: " + x_quote_header_id[0]);
}
      headerRec.quote_header_id  = x_quote_header_id[0];
      this.cartId = headerRec.quote_header_id.toString();
      if (lineRec != null && lineRec.length > 0 && x_ql_quote_line_id != null && x_ql_quote_line_id.length >0)
      {
        int lineLen = x_ql_quote_line_id.length;
        for (int l = 0; l < lineLen; l++) {
          lineRec[l].quote_line_id = x_ql_quote_line_id[l];
          shopCartItems[l].setCartLineId(x_ql_quote_line_id[l].toString());
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineid[" + l + "]: " + x_ql_quote_line_id[l]);
}
        }
      }
    }
    finally
    {
      headerRec.last_update_date = x_last_update_date[0];
      if (ocs != null) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "calling ocs.close()");
}
        ocs.close();
      }
      if(conn != null)
      {
        TransactionScope.releaseConnection(conn);
      }

      if(logEnabled) { IBEUtil.log("CLASS",
                  "METHOD",
                  "End(" + (System.currentTimeMillis() - startTime) + " milliseconds)");
}
    }
  }

  /**
   * addBundles<BR>
   * <BR>
   * Adds one or more Model Bundle items to a cart by calling addModelsToCart and
   * has the same requirements in terms of member variables needing to be set.<BR>
   * <BR>
   * @param calcTaxFlag - If <CODE>YES</CODE>, calculates tax. If <CODE>NO</CODE>, does not.
   * @param calcFrieghtFlag - If <CODE>YES</CODE>, calculates shipping costs. If <CODE>NO</CODE>, does not.
   * @param priceRecalcFlag - boolean to drive repricing of the cart
   * @param combineSameItem  If <CODE>COMBINED_LINES</CODE>, it combines lines of
   * the same item.  If <CODE>SEPARATE_LINES</CODE>, it creates seperate lines for
   * the same item.  If <CODE>USE_PROFILE</CODE> or any other values, it uses the
   * user profile <CODE>IBE_SC_MERGE_SHOPCART_LINES</CODE> determine whether to
   * combines lines or create seperate lines.  This parameter does not affect
   * configurable and service items.
   *
   * @return - none, but the headerRec will have the quote_header_id set, and the shopCartItems will have cartLineIds set
   *
   * @throws SQLException - if there is a database error
   * @throws FrameworkException - if there is a framework layer error
   * @throws ShoppingCartException - the error message will indicate the nature of the error
   *
   */
  public void addBundles(String calcTaxFlag,
                   String calcFrieghtFlag,
                   boolean priceRecalcFlag,
                   int        combineSameItem)
  throws SQLException, FrameworkException, ShoppingCartException
  {
    String  METHOD = "addModelsToCart";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
}
    addModelsToCart(calcTaxFlag,calcFrieghtFlag,priceRecalcFlag,combineSameItem,true);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }

  /**
   * Updates the commitment info for the lines in the shopping cart.<BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method. however, this should not be set if express ordering the items
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <LI>shopCartItems array (optional) via setShoppingCartItems method (Each ShoppingCartItem will
   * need to contain the cart line id) to deal with line level commitments.<BR>
   * <BR>
   * To save or blank out commitments for lines, the
   * developer will need to set the shopping cart items in the cart using the
   * setShoppingCartItems method and then call this API.
   * Each ShoppingCartItem will need to contain the cart line id and optionally the
   * commitment id set via setCommitmentId either to null (to remove the commitment)
   * or to the id of the commitment to be applied and saved.<BR>
   * <BR>
   * @param - priceRecalcFlag - indicates whether price needs to be calculated
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateCommitments(boolean priceRecalcFlag)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    updateCommitmentsAgreements(priceRecalcFlag, true, true);
  }

  /**
   * Updates the agreement info for the shopping cart.<BR>
   * <BR>
   * Instance method that expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method. however, this should not be set if express ordering the items
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp
   * <LI>agreementInfo object (optional) via setAgreementInfo to deal with the header agreement
   * <LI>shopCartItems array (optional) via setShoppingCartItems method (Each ShoppingCartItem will
   * need to contain the cart line id) to deal with line level agreements.
   * <BR><BR>
   * To save (or blank out) the header Agreement info,
   * setAgreementInfo must have been called with an Agreement object containing either
   * an agreementID with a value to save, or a null value (to blank out the header agreement).
   * A null ShoppingCart.agreementInfo object will signify a no-op for the header agreement.<BR>
   * <BR>
   * To save or blank out agreements for lines, the
   * developer will need to set the shopping cart items in the cart using the
   * setShoppingCartItems method and then call this API.
   * Each ShoppingCartItem will need to contain the cart line id and optionally the
   * agreement id set via setAgreementId either to null (to remove the agreement)
   * or to the id of the agreement to be applied and saved.<BR>
   * <BR>
   * @param - priceRecalcFlag - indicates whether price needs to be calculated
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  public void updateAgreements(boolean priceRecalcFlag)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    updateCommitmentsAgreements(priceRecalcFlag, false, true);
  }

  /**
   * Updates the commitment and agreement info for the lines in the shopping cart.<BR>
   * <BR>
   * To save (or blank out) the header Agreement info,
   * setAgreementInfo must have been called with an Agreement object containing either
   * an agreementID with a value to save, or a null value (to blank out the header agreement).
   * A null ShoppingCart.agreementInfo object will signify a no-op for the header agreement.<BR>
   * <BR>
   * To save or blank out agreements and/or commitments for lines, the
   * developer will need to set the shopping cart items in the cart using the
   * setShoppingCartItems method and then call this API.
   * Each ShoppingCartItem will need to contain the cart line id and optionally the
   * commitment id and agreement id.<BR>
   * <BR>
   * @param - priceRecalcFlag - indicates whether price needs to be calculated
   * @param - saveCommitments - indicates whether to save Commitment info
   * @param - saveAgreements - indicates whether to save Agreement info<BR>
   *          for this flag, a false value will not save header or line agreement<BR>
   *          a true value will check for a non-null ShoppingCart.agreementInfo (set by setAgreementInfo)
   *          and save the header info if there is an object set;
   *          and will send down all line Agreement info as set in the ShoppingCartItem objects.
   * @throws FrameworkException - if there is a framework layer error
   * @throws SQLException - if there is a database error
   * @throws QuoteException - the error message will indicate the nature of the error
   * @throws ShoppingCartException - the error message will indicate the nature of
   * of the error
   */
  private void updateCommitmentsAgreements(boolean priceRecalcFlag, boolean saveCommitments, boolean saveAgreements)
          throws FrameworkException, SQLException, QuoteException,
                 ShoppingCartException
  {
    String  METHOD = "updateCommitmentsAgreements";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");
    IBEUtil.log(CLASS, METHOD, "cartId = " + cartId);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "priceRecalcFlag = " + priceRecalcFlag);
    IBEUtil.log(CLASS, METHOD, "saveCommitments = " + saveCommitments);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "saveAgreements  = " + saveAgreements);

}
    ShoppingCartItem  scartItem = null;

    try {
      BigDecimal  quoteHeaderId = null;
      if (cartId != null && !EMPTY_STRING.equals(cartId))
        quoteHeaderId = new BigDecimal(cartId);

      if(shopCartItems != null)
      {
        int numCartItems = shopCartItems.length;
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "num of items being updated = " + numCartItems);
}
        if(numCartItems > 0)
        {
          if(lineRec == null)
          {
            lineRec = new LineRecord[numCartItems];
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numCartItems is " + numCartItems);
}
          }
          String currCommitId = null;
          String currAgreeId = null;
          for(int i = 0; i < numCartItems; i++)
          {
            scartItem = shopCartItems[i];

            if(lineRec[i] == null)
            {
              lineRec[i] =
                new oracle.apps.ibe.shoppingcart.quote.LineRecord();
            }

            ShoppingCartUtil.setupLineRecord(lineRec[i],
                                             UPDATE_OPCODE, quoteHeaderId,
                                             new BigDecimal(scartItem.cartLineId),
                                             null,
                                             null,
                                             null, null,
                                             null, null, null,
                                             null);
            if (saveCommitments) {
              currCommitId = scartItem.getCommitmentId();
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, " commmitment_id to save: " + currCommitId);
}
              if (currCommitId != null && !EMPTY_STRING.equals(currCommitId))
                lineRec[i].commitment_id = new BigDecimal(currCommitId);
              else
                lineRec[i].commitment_id = null;
            }

            if (saveAgreements) {
              currAgreeId =scartItem.getAgreementId();
              if(logEnabled) { IBEUtil.log(CLASS, METHOD, " agreement_id to save: " + currAgreeId);
}
              if (currAgreeId != null && !EMPTY_STRING.equals(currAgreeId))
                lineRec[i].agreement_id = new BigDecimal(currAgreeId);
              else
                lineRec[i].agreement_id = null;
            }
          }   // end of for loop
        } // end if(numCartItems > 0)
      } // end if(shopCartItems != null)

      String  calcTax = YES;
      String  calcFreight = YES;
      if (!priceRecalcFlag) {
        calcTax = NO;
        calcFreight = NO;
      }
      setupControlRecord(calcTax, calcFreight, priceRecalcFlag);
      setupHeaderRecord(quoteHeaderId);

      if (saveAgreements && (agreementInfo != null)) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, " header agreement to save: " + agreementInfo.agreementID);
}
        headerRec.contract_id = agreementInfo.agreementID;
        // if nulling out the header agreement, reset the price list id
        if (agreementInfo.agreementID == null) {
          headerRec.price_list_id = StoreMinisite.getPriceListID();
        }
      }

      // save the quote
      BigDecimal  shareeNumber = RequestCtx.getShareeNumber();
      BigDecimal  partyId = null;
      BigDecimal  acctId = null;

      if(shareeNumber != null)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Is a sharee");
}
        partyId = RequestCtx.getPartyId();
        acctId = RequestCtx.getAccountId();
      }

      try
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.save");
}
        save(partyId, acctId, shareeNumber, Quote.SEPARATE_LINES, false,
             true, false, false, false, false, false, false, false,
             false, false, false, false, false, false, false, false,
             false);
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling Quote.save");

}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
    }
    catch(NumberFormatException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      throw new ShoppingCartException("IBE_INVALID_NUMBER", e);
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
  }
/**
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.headerBilling   = true;<BR>
    loadControlRec.loadItems       = loadLineBilling;<BR>
    loadControlRec.lineBilling     = loadLineBilling;<BR>
    loadControlRec.fillChildItems  = false;<BR>
    loadControlRec.showPrice       = false;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;
  */
  public static final ShoppingCart loadWithBilling(String cartId, boolean loadLineBilling)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadWithBilling";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "input: cartId: " + cartId + " loadLineBilling: " + loadLineBilling);
}
    ShoppingCart shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.headerBilling   = true;
    loadControlRec.loadItems       = loadLineBilling;
    loadControlRec.lineBilling     = loadLineBilling;
    loadControlRec.fillChildItems  = false;
    loadControlRec.showPrice       = false;
    loadControlRec.defaultPreferences = true;
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;

    shopCart = loadAndFill(cartId, null,getRetrievalNumberString(), loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;
  }
  /**
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems       = true;<BR>
    loadControlRec.headerBilling   = true;<BR>
    loadControlRec.lineBilling     = true;<BR>
    loadControlRec.fillChildItems  = false;<BR>
    loadControlRec.showPrice       = false;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;<BR>
    String[] cartLineIds = new String[1];<BR>
    cartLineIds[0] = cartLineId;<BR>
    shopCart = loadAndFill(cartId, cartLineIds, getRetrievalNumberString(),loadControlRec);<BR>
   */
  public static final ShoppingCart loadLineWithBilling(String cartId, String cartLineId)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadLineWithBilling";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called input: cartId: " + cartId + " cartLineId: " + cartLineId);

}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.loadItems       = true;
    loadControlRec.headerBilling   = true;
    loadControlRec.lineBilling     = true;
    loadControlRec.fillChildItems  = false;
    loadControlRec.showPrice       = false;
    loadControlRec.defaultPreferences = true;
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;
    String[] cartLineIds = new String[1];
    cartLineIds[0] = cartLineId;
    shopCart = loadAndFill(cartId, cartLineIds, getRetrievalNumberString(),loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;
  }

  /**
   * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.loadItems       = true;<BR>
    loadControlRec.headerShipping  = true;<BR>
    loadControlRec.lineShipping    = true;<BR>
    loadControlRec.fillChildItems  = false;<BR>
    loadControlRec.showPrice       = false;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;<BR>
    String[] cartLineIds = new String[1];<BR>
    cartLineIds[0] = cartLineId;<BR>
    shopCart = loadAndFill(cartId, cartLineIds,getRetrievalNumberString(), loadControlRec);<BR>
   */
  public static final ShoppingCart loadLineWithShipping(String cartId, String cartLineId)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadLineWithShipping";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called input: cartId: " + cartId + " cartLineId: " + cartLineId);

}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.loadItems       = true;
    loadControlRec.headerShipping  = true;
    loadControlRec.lineShipping    = true;
    loadControlRec.fillChildItems  = false;
    loadControlRec.showPrice       = false;
    loadControlRec.defaultPreferences = true;
    loadControlRec.loadType        = ShoppingCart.LOAD_CART;
    String[] cartLineIds = new String[1];
    cartLineIds[0] = cartLineId;
    shopCart = loadAndFill(cartId, cartLineIds,getRetrievalNumberString(), loadControlRec);

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);

}
    return shopCart;
  }


  public static ShoppingCart[] loadAllQuotes()
  throws SQLException, FrameworkException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadAllQuotes";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Start");
}
    return loadAllCarts(false,false,true,true,LOAD_QUOTE);
  }


  // use either ShoppingCart.LOAD_CART or LOAD_QUOTE
  /**
  * @deprecated should use load (#6) with the control record set as such:<BR>
    loadControlRec.fillChildItems  = true;<BR>
    loadControlRec.loadItems       = true;<BR>
    loadControlRec.showPrice       = true;<BR>
    loadControlRec.defaultPreferences = true;<BR>
    loadControlRec.lineTax         = loadLineTaxDetails;<BR>
    loadControlRec.loadType        = loadType;
 */
  public static final ShoppingCart loadWithItems(String cartId,
                                                 boolean loadLineTaxDetails,
                                                 int loadType)
  throws FrameworkException, SQLException, QuoteException, ShoppingCartException
  {
    String METHOD = "loadWithItems";
    boolean logEnabled = IBEUtil.logEnabled();
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "input: cartId: " + cartId + " loadLineTaxDetails: " + loadLineTaxDetails + " loadType: " + loadType);

}
    ShoppingCart  shopCart = null;
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.fillChildItems  = true;
    loadControlRec.loadItems       = true;
    loadControlRec.showPrice       = true;
    loadControlRec.defaultPreferences = true;
    loadControlRec.lineTax         = loadLineTaxDetails;
    loadControlRec.loadType        = loadType;

    shopCart = loadAndFill(cartId, null, getRetrievalNumberString(),loadControlRec);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE shopCart: " + shopCart);
}
    return shopCart;
  }

 /** saveHeaderPayment - api to save payment information only; intended for use
   * in updating a published quote's payment information.
   *
   * New Behavior: for paymentId, if <CODE>NOOP_STRING</CODE> is passed,
   * the corresponding record type will not be set nor sent to the database to be saved.
   * This would mean that the poNumber as well as any CreditCard or other payment information
   * would not be saved or updated. This is intended to save the jdbc preparation
   * calls in the Quote.java object, throughput to the database,
   * and update operations in the plsql layer.<BR>
   * <BR>
   * Otherwise, a <CODE>null</CODE> or <CODE>EMPTY_STRING</CODE> value will trigger a create
   * operation, any other non-null, non-EMPTY_STRING, non-NOOP_STRING will do an update.<BR>
   * <BR>
   * A call to this api would be unnecessary if no changes to the payment information
   * needed to be saved.
   *
   */
  public void saveHeaderPayment(String cartId,
                                String lastModifiedTimestamp,
                                String paymentId,  // if updating existing payment record
                                String paymentType, // use defined types in ShoppingCart object (PO_PAYMENT, etc)
                                String paymentNumber, // if Check or PO number is being given
                                CCPayment cc,   // in the event that a Credit Card is being used
                                String poNumber,
                                boolean calcTaxAndReprice)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    saveHeaderPayment(cartId,
                      lastModifiedTimestamp,
                      paymentId,
                      paymentType,
                      paymentNumber,
                      cc,   
                      poNumber,
                      calcTaxAndReprice,
                      getRetrievalNumberString());
  }

  public void saveHeaderPayment(String cartId,
                                String lastModifiedTimestamp,
                                String paymentId,  // if updating existing payment record
                                String paymentType, // use defined types in ShoppingCart object (PO_PAYMENT, etc)
                                String paymentNumber, // if Check or PO number is being given
                                CCPayment cc,   // in the event that a Credit Card is being used
                                String poNumber,
                                boolean calcTaxAndReprice,
                                String retrievalNumber)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    String METHOD = "saveHeaderPayment";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called! input: ");
    IBEUtil.log(CLASS, METHOD, "  cartId                : " + cartId);
    IBEUtil.log(CLASS, METHOD, "  lastModifiedTimestamp : " + lastModifiedTimestamp);
    IBEUtil.log(CLASS, METHOD, "  paymentId             : " + paymentId);
    IBEUtil.log(CLASS, METHOD, "  paymentType           : " + paymentType);
    IBEUtil.log(CLASS, METHOD, "  paymentNumber         : " + paymentNumber);
    IBEUtil.log(CLASS, METHOD, "  cc object             : " + cc);
    IBEUtil.log(CLASS, METHOD, "  reprice flag          : " + calcTaxAndReprice);
    IBEUtil.log(CLASS, METHOD, "  retrievalNumber       : " + retrievalNumber);
      }
    // parameters that were not passed in to this api, will belong to the "this" object and be empty strings
    // and that's okay because the savePaymentInformation has savePaymentOnly set to true, so it will
    // not look at the tax & billing parameters.
    setupShopCartPaymentInfo(cartId, paymentId, NOOP_STRING, false, "", "",
                             lastModifiedTimestamp, billtoCustomerAccountId,
                             billtoContactPartyId, billtoPartySiteId,
                             billtoPartySiteType, poNumber);

    this.cc = cc;

    this.paymentType = paymentType;
    this.paymentNumber = paymentNumber;
    this.saveType = SAVE_PAYMENT_ONLY;

    savePaymentInformation(true, calcTaxAndReprice,retrievalNumber);
  }

  /** original signature (#1) - calls #2 with null retrievalURL*/
  public void requestSalesAssistance(String reason, String comments, BigDecimal minisiteId)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    this.requestSalesAssistance(reason, comments, minisiteId, null);
  }

  /** #2 - original signature + retrievalURL calls #3 with null retrievalNumber*/
  public void requestSalesAssistance(String reason, String comments, BigDecimal minisiteId, String retrievalURL)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    this.requestSalesAssistance(reason, comments, minisiteId, retrievalURL, null);
  }
  /** #3 - original signature +  retrievalURL, retrievalNumber; calls #4 with contractContext false and notes set to null */
  public void requestSalesAssistance(String reason, String comments, BigDecimal minisiteId, String retrievalURL, String retrievalNumber)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    this.requestSalesAssistance(reason, comments, minisiteId, retrievalURL,retrievalNumber, false,null);
  }
  /** Requests Sales Assistance on the cart. The method expects the following to be set:
   * <LI>cartId via a constructor or the setCartId method.
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp.
   * <LI>cartName via the setCartName method.
   * <LI>lastModifiedTimestamp via a contructor or the setLastModifiedTimeStamp.
   * When a user requests sales assistance on a cart , the cart is converted into a quote and a sales representative
   * is assigned to it.
   * The cart no longer appears on the list of "My Carts", but moves to the list of "My Quotes".
   * Email notifications go to the sales representative and the initiator of the request (whether owner or administrator).
   * The email to the sales representative contains the reason, comments, and contact info of
   * the initiator.  If the cart is shared, then the access level of all members of the shared cart,
   * except the administrator who submitted the request, is downgraded to 'readonly' ).
   * 
   * @param reason The reason for requesting sales assistance.
   * @param comments The comments entered by the user while requesting for sales assistance.
   * @param minisiteId The unique identifier of the site in which sales assistance was requested.
   * @param retrievalURL The URL to retrieve the cart from a notification.
   * @param retrievalNumber The unique number given to the member of a shared cart.
   * @param contractContext The flag to determine contract context.
   * @param notes The notes exchanged during the interaction between the user and the sales representative.
   * @throws FrameworkException If there is a framework layer error
   * @throws SQLException If there is a database error
   * @throws QuoteException The error message will indicate the nature of the error
   * @throws ShoppingCartException The error message will indicate the nature of
   * of the error
   * @rep:displayname Request Sales Assistance
   */
   //  1/7/03 - added retrieval URL in case the cart was shared
   // 7/23/03 - added retrieval Number in case the cart was shared, as the administrator can ask for RSA
   // 10/16/03 - added contractContext and notes parameters
   //
  public void requestSalesAssistance(String reason, String comments, BigDecimal minisiteId,
   String retrievalURL, String retrievalNumber, boolean contractContext, String notes)
  throws FrameworkException, SQLException, ShoppingCartException, QuoteException
  {
    String METHOD = "requestSalesAssistance";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Called! input: ");
    IBEUtil.log(CLASS, METHOD, "  cartId     : " + this.cartId);
   IBEUtil.log(CLASS, METHOD, "  cartName   : " + this.cartName);
    IBEUtil.log(CLASS, METHOD, "  reason     : " + reason);
   IBEUtil.log(CLASS, METHOD, "  comments   : " + comments);
    IBEUtil.log(CLASS, METHOD, "  minisiteId : " + minisiteId);
   IBEUtil.log(CLASS, METHOD, "  retrievalURL : " + retrievalURL);
    IBEUtil.log(CLASS, METHOD, "  retrievalNumber : " + retrievalNumber);
    IBEUtil.log(CLASS, METHOD, "  contractContext : " + contractContext);
    IBEUtil.log(CLASS, METHOD, "  notes : " + notes);
    }
    BigDecimal retrievalNumber_b = makeRetrievalNumBigDecimal(retrievalNumber);

    String[]                x_return_status = new String[1];
    int[]                   x_msg_count = new int[1];
    String[]                x_msg_data = new String[1];
    Timestamp[]             x_last_update_date = new Timestamp[1];

    OracleCallableStatement ocs = null;
    BigDecimal              quoteHeaderId = null;
    if (cartId != null) {
      quoteHeaderId = new BigDecimal(cartId);
    } else {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "INTERNAL ERROR: null cartId");
}
    }

    Timestamp lastUpdateDate = Timestamp.valueOf(lastModifiedTimestamp);

    try
    {
      OracleConnection  conn =
        (OracleConnection) TransactionScope.getConnection();

      conn.setAutoCommit(false);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "About to call PL/SQL IBE_QUOTE_SAVE_pvt.request_sales_assistance api");
}
      int i = 1;
      StringBuffer  ocsStmt = new StringBuffer(100);
      ocsStmt.append("BEGIN " + IBEUtil.getEnableDebugString() + " IBE_QUOTE_SAVE_pvt.request_for_sales_assistance ( "
                         + "P_Api_Version => 1.0, "
                         + "p_Init_Msg_List => FND_API.G_FALSE, "
                         + "p_Commit => FND_API.G_FALSE, "
                         + "x_return_status   => :" + i++ +", "
                         + "x_msg_count       => :" + i++ +", "
                         + "x_msg_data        => :" + i++ +", "
                         + "p_last_update_date => :" + i++ +", "
                         + "p_quote_header_id => :" + i++ +", "
                         + "p_party_Id => :" + i++ +", "
                         + "p_cust_account_Id => :" + i++ +", "
                         + "p_validate_user => FND_API.G_TRUE, "
                         + "P_quote_name => :" + i++ +", "
                         + "P_reason_code => :" + i++ +", "
                         + "P_COMMENTS => :" + i++ +", "
                         + "p_minisite_id => :" + i++ +", " 
                         + "x_last_update_date => :" + i++);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "marker1 i: " + i);

}
      if ((retrievalURL != null) && (!"".equals(retrievalURL.trim()))) {
        ocsStmt.append(",p_url => :" + i++ );
      }

      if (retrievalNumber_b != null)
      {
        ocsStmt.append(",p_retrieval_number => :" + i++ );
      }
      if (contractContext)
      {
        ocsStmt.append(",p_contract_context => 'Y' ");
      }
      if (notes != null)
      {
        ocsStmt.append(",p_notes => :" + i++ );
      }

      ocsStmt.append("); " + IBEUtil.getDisableDebugString() + " END;");
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "marker2 i: " + i);
      IBEUtil.log(CLASS, METHOD, "ocsStmt.toString(): " + ocsStmt.toString());
}
      ocs = (OracleCallableStatement) conn.prepareCall(ocsStmt.toString());

      // register types of OUT and IN-OUT, if any
      ocs.registerOutParameter(1, OracleTypes.VARCHAR,0,IBEUtil.RTN_STATUS_MAXLENGTH);
      ocs.registerOutParameter(2, OracleTypes.NUMBER);
      ocs.registerOutParameter(3, OracleTypes.VARCHAR,0,IBEUtil.MSGDATA_MAXLENGTH);
      ocs.registerOutParameter(12, OracleTypes.TIMESTAMP);

      // register IN or IN-OUT params, if any
      ocs.setTimestamp(4, lastUpdateDate);
      ocs.setBigDecimal(5, quoteHeaderId);
      ocs.setBigDecimal(6, RequestCtx.getPartyId());
      ocs.setBigDecimal(7, RequestCtx.getAccountId());
      ocs.setString(8, cartName);
      ocs.setString(9, reason);
      ocs.setString(10, comments);
      ocs.setBigDecimal(11, minisiteId);
      i = 13;
      if ((retrievalURL != null) && (!"".equals(retrievalURL.trim()))) {
        ocs.setString(i++, retrievalURL);
      }
      if (retrievalNumber_b != null)
      {
        ocs.setBigDecimal(i++,retrievalNumber_b);
      }
      if (notes != null)
      {
        ocs.setString(i++, notes);
      }
if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Before Execute");
}
      ocs.execute();
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "After Execute");

}
      // get OUT and IN-OUT params, if any
      x_return_status[0] = ocs.getString(1);
      x_msg_count[0] = ocs.getInt(2);
      x_msg_data[0] = ocs.getString(3);
      x_last_update_date[0] = ocs.getTimestamp(12);

      if(logEnabled) { IBEUtil.log(CLASS, METHOD,"x_return_status[0]   :" + x_return_status[0]);
      IBEUtil.log(CLASS, METHOD,"x_msg_count[0]       :" + x_msg_count[0]);
     IBEUtil.log(CLASS, METHOD,"x_last_update_date[0]:" + x_last_update_date[0]);
 IBEUtil.log(CLASS, METHOD, "Done calling PL/SQL IBE_QUOTE_SAVE_pvt.request_sales_assistance api");

}
      if(!FndConstant.getGRetStsSuccess().equals(x_return_status[0]))
      {
        if (ocs != null)
        {
//          Timestamp lastUpdateDateFromDB = x_last_update_date[0];
          if ((x_last_update_date[0] != null)
            && (x_last_update_date[0].after(lastUpdateDate)) )
          {
            reloadFlag = true;
            throw new ShoppingCartException(x_msg_count[0], (String) null);
          }
        }
        if(x_msg_count[0] > 1)
        {
          if(FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
          {
            throw ErrorStackUtil.getDBFrameworkException(x_msg_count[0]);
          }
          else
          {
            throw new QuoteException(x_msg_count[0], (String) null);
          }
        }
        else
        {
          if(FndConstant.getGRetStsUnexpError().equals(x_return_status[0]))
          {
            throw new FrameworkException(0, x_msg_data[0]);
          }
          else
          {
            throw new QuoteException(x_msg_count[0], (String) null);
          }
        }
      }
    }
    finally
    {
      if(ocs != null)
      {
        ocs.close();
      }
    }
  }

 /**
  * Adds items to the cart or express orders depending on the addToCartContext setting.
  * The method expects the following to be set:
  * <LI>cartId using a constructor or the setCartId method. However, this should not be set if express ordering the items.
  * <LI>lastModifiedTimestamp using a contructor or the setLastModifiedTimeStamp method.
  * <LI>shopCartItems array using setShoppingCartItems method to add standard items with or without services, configurable model items, and bundle items.
  * <LI>addToCartContext using setAddToCartContext to set the value to either ShoppingCart.ADDTO_ACTIVE_CART if this is an active cart,
  * or ShoppingCart.ADDTO_EXPRESS_ORDER if this is a new express order, or ShoppingCart.UPDATE_EXPRESS_ORDER if consolidating items into a pending express order.
  *
  * @return The unique identifier of the cart either updated or created as a result of this add operation.
  * @throws FrameworkException if there is a framework layer error
  * @throws SQLException if there is a database error
  * @throws ShoppingCartException the error message will indicate the nature of the error
  * @rep:displayname Add Items to Cart
  */
  public String addItemsToCart()
  throws FrameworkException, ShoppingCartException, SQLException
  {
    String METHOD = "addItemsToCart";
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "CALLED");

}
//-------------- Setup MinisiteId --------------------------------------------//
  if((cartId == null || EMPTY_STRING.equals(cartId)) && 
       (headerRec == null || (headerRec != null && (headerRec.minisite_id == null || gMissNum.equals(headerRec.minisite_id)))))
  {
    if(headerRec == null)
    {
      headerRec = new HeaderRecord();
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Minisite_Id is set for the new quote: "+RequestCtx.getMinisiteId());
}
    headerRec.minisite_id = RequestCtx.getMinisiteId();
  }

//--------------  setup control & header records -----------------------------//
    setupControlHeaderLineRecords(YES,  // calcTaxFlag
                                  YES,  // calcFrieghtFlag
                                  true); // priceRecalcFlag
    boolean saveLineDetailRecords = false;
    if (lineDetRec != null && lineDetRec.length > 0) saveLineDetailRecords = true;

//--------------  setup promocodes  -----------------------------//
    boolean saveLinePriceAttr = false;
    saveLinePriceAttr = setupLinePromotionCodes(); //(this api only takes care of line promocodes for now)

//----------------------  prepare & execute jdbc call ------------------------//
    BigDecimal shareeNumber = RequestCtx.getShareeNumber();
    BigDecimal shareePartyID = null;
    BigDecimal shareeCustAcctID = null;
    if (shareeNumber != null) {
      shareePartyID = RequestCtx.getPartyId();
      shareeCustAcctID = RequestCtx.getAccountId();
    }
    int saveType = SAVE_ADDTOCART;
    if (addToCartContext == this.ADDTO_EXPRESS_ORDER) saveType = this.SAVE_EXPRESSORDER;
    if (addToCartContext == this.UPDATE_EXPRESS_ORDER) saveType = this.UPDATE_EXPRESSORDER;

    try
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Quote.save");
}
      save(shareePartyID, shareeCustAcctID, shareeNumber, Quote.USE_PROFILE, false,
           true, saveLineDetailRecords, false, saveLinePriceAttr, false, false, false, false,
           false, false, false, false, false, false, false, false,
           false, saveType);
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done Calling Quote.save");
}
    }
    catch(QuoteException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Checking for timestamp problem");
}
      checkUpdateTimestamp(e);
    }
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE: cartId: " + headerRec.quote_header_id);
}
    return headerRec.quote_header_id.toString();
  }

  /**
   *  Private helper function for addModelsToCart and addItemsToCart to use to set
   *  the control record, header record, and the line records from the ShoppingCart
   *  object into the Quote object; and ShoppingCartItems into LineRecords.
   *
   */
  private void setupControlHeaderLineRecords(String calcTaxFlag,String calcFrieghtFlag,boolean priceRecalcFlag)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    String METHOD = "setupControlHeaderLineRecords";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "calcTaxFlag     = " + calcTaxFlag);
    IBEUtil.log(CLASS, METHOD, "calcFrieghtFlag = " + calcFrieghtFlag);
    IBEUtil.log(CLASS, METHOD, "priceRecalcFlag = " + priceRecalcFlag);
    IBEUtil.log(CLASS, METHOD, "calling setup routines...");
}
    setupControlRecord(calcTaxFlag,calcFrieghtFlag,priceRecalcFlag);
    BigDecimal bigCartId = gMissNum;
    try {
      if (cartId.equals(EMPTY_STRING))
        cartId = gMissChar;
      else
        bigCartId = new BigDecimal(cartId);
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }

    setupHeaderRecord(bigCartId);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "done with setup routines");

}
    if (cartId == gMissChar) {
      if (!RequestCtx.userIsLoggedIn())
        headerRec.quote_source_code = "IStore Walkin";
      else
        headerRec.quote_source_code = "IStore Account";

      if (addToCartContext == this.ADDTO_EXPRESS_ORDER)
        headerRec.quote_source_code = "IStore Oneclick";
    }

//----------------------  setup line records --------------------------------//
    if((shopCartItems == null) || (shopCartItems.length == 0))
      throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");

    // changed the way we count the numOfItems now that we support
    // noop and service items coming in as children
    int sciArrayLen = shopCartItems.length;
    int numOfItems = 0; // total number of items we want to actually "create"
    int numOfItemDetails = 0 ; // total number of line record to "create"
    int sciCounter = 0; // counter thru the ShoppingCartItem array
    int svcCounter = 0; // counter thru the ServiceItem arrays (if any)
    int lineRecCounter = 0; // counter thru the LineRecord array
    int lineDetCounter = 0; // counter thru the LineDetailRecord array
    ServiceItem [] svcItems = null; // placeholder for child "for" loops
    // check for service items that we may need to create
    for (sciCounter = 0; sciCounter < sciArrayLen; sciCounter ++) {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getQuantity()        " + shopCartItems[sciCounter].getQuantity());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getInventoryItemId() " + shopCartItems[sciCounter].getInventoryItemId());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getOrganizationId()  " + shopCartItems[sciCounter].getOrganizationId());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getUom()             " + shopCartItems[sciCounter].getUom());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getItemType()        " + shopCartItems[sciCounter].getItemType());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getMinisiteId()      " + shopCartItems[sciCounter].getMinisiteId());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getSectionId()       " + shopCartItems[sciCounter].getSectionId());
      IBEUtil.log(CLASS, METHOD, "shopCartItems["+sciCounter+"].getOperationCode()   " + shopCartItems[sciCounter].getOperationCode());
          }//ibeutil.log
      if (!ShoppingCartItem.NOOP_OPCODE.equals(shopCartItems[sciCounter].getOperationCode())) {
       numOfItems++;
      }
      if ((shopCartItems[sciCounter].svcItems != null) && (shopCartItems[sciCounter].svcItems.length >= 1)) {
        svcItems = shopCartItems[sciCounter].svcItems;
        for (svcCounter = 0; svcCounter < svcItems.length; svcCounter ++) {
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getQuantity()        " + svcItems[svcCounter].getQuantity());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getInventoryItemId() " + svcItems[svcCounter].getInventoryItemId());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getOrganizationId()  " + svcItems[svcCounter].getOrganizationId());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getUom()             " + svcItems[svcCounter].getUom());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getItemType()        " + svcItems[svcCounter].getItemType());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getMinisiteId()      " + svcItems[svcCounter].getMinisiteId());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getSectionId()       " + svcItems[svcCounter].getSectionId());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getOperationCode()   " + svcItems[svcCounter].getOperationCode());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getServiceReferenceType()       " + svcItems[svcCounter].getServiceReferenceType());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getServicePeriod()   " + svcItems[svcCounter].getServicePeriod());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getServiceDuration() " + svcItems[svcCounter].getServiceDuration());
          IBEUtil.log(CLASS, METHOD, "svcItems["+svcCounter+"].getServiceReferenceLineId() " + svcItems[svcCounter].getServiceReferenceLineId());
          }//ibeutil
          if (!ShoppingCartItem.NOOP_OPCODE.equals(svcItems[svcCounter].getOperationCode())) {
            numOfItems++;
            numOfItemDetails++;
          }
        }// end for loop over child services
      }
    } // end for loop over parent shopCartItems

/* NOTE: We should eventually validate the quantities coming in
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling Item.validateQuantity");
}
          Item.validateQuantity(itemIds, orgIds, strQuantities, uomCodes);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Done calling Item.validateQuantity");
}
*/
    if(lineRec == null) {
      lineRec = new LineRecord[numOfItems];
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numOfItems is " + numOfItems);
}
    }
    if(this.lineDetRec == null) {
      lineDetRec = new LineDetailRecord[numOfItemDetails];
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "numOfItemDetails is " + numOfItemDetails);
}
    }

//    for (int i = 0; i < numOfItems; i ++) {
//      former logging loop over shopCartItems
//    }
    lineRecCounter = 0;
    lineDetCounter = 0;
    for (sciCounter = 0; sciCounter < sciArrayLen; sciCounter ++) {

      // IDEALLY, THESE 2 CALLS REALLY SHOULDN'T BE HERE; THESE SHOULD BE SET LIKE THIS BY CALLER
      // will leave them here as extra check since we're hard-coded to "CREATE" anyways.

      //      shopCartItems[i].setCartLineId(EMPTY_STRING);  ??? Non backward compatable change???

//      shopCartItems[sciCounter].setStartDateActive(EMPTY_STRING);
      if (!ShoppingCartItem.NOOP_OPCODE.equals(shopCartItems[sciCounter].getOperationCode())) {
        lineRec[lineRecCounter] = new LineRecord();
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "setupLineRecord for shopCartItem; lineRecCounter : " + lineRecCounter );
}
        ShoppingCartUtil.setupLineRecord(lineRec[lineRecCounter],
                                         this.CREATE_OPCODE,
                                         cartId,
                                         shopCartItems[sciCounter]
                                         );
        lineRecCounter++;
      }
      if ((shopCartItems[sciCounter].svcItems != null) && (shopCartItems[sciCounter].svcItems.length >= 1)) {
        //########## ADD CHECK HERE LATER IF PROFILE DOESN'T ALLOW MORE THAN ONE SVC per SVA#############
        svcItems = shopCartItems[sciCounter].svcItems;
        for (svcCounter = 0; svcCounter < svcItems.length; svcCounter++) {
          if (!ShoppingCartItem.NOOP_OPCODE.equals(svcItems[svcCounter].getOperationCode())) {
            lineRec[lineRecCounter] = new LineRecord();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "setupLineRecord for svcItem;  lineRecCounter : " + lineRecCounter );
}
       /*     if (!ShoppingCartItem.NOOP_OPCODE.equals(shopCartItems[sciCounter].getOperationCode())) {
            svcItems[svcCounter].setQuantity(shopCartItems[sciCounter].getQuantity());
          }*/
            svcItems[svcCounter].setItemType(ShoppingCartItem.SERVICE_ITEM_TYPE);
            ShoppingCartUtil.setupLineRecord(lineRec[lineRecCounter],
                                             this.CREATE_OPCODE,
                                             cartId,
                                             svcItems[svcCounter]
                                             );
            lineRecCounter++;
            // then setupLineRecord for svcItem
            // and setupLineDetailRecord for the service item
            lineDetRec[lineDetCounter] = new LineDetailRecord();
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "setupLineDetailRecord for svcItem;  lineRecCounter : " + lineRecCounter );
}
            BigDecimal svaIndex = null;
            BigDecimal svaLineId = null;
            if (!EMPTY_STRING.equals(svcItems[svcCounter].getServiceReferenceLineId())) {
              svaLineId = new BigDecimal(svcItems[svcCounter].getServiceReferenceLineId());
            } else if (!EMPTY_STRING.equals(shopCartItems[sciCounter].cartLineId)) {
              svaLineId = new BigDecimal(shopCartItems[sciCounter].cartLineId);
            } else {
              svaIndex = new BigDecimal(lineRecCounter-1);
            }
            setupLineDetailRecord(lineDetRec[lineDetCounter],
                                  svcItems[svcCounter],
                                  this.CREATE_OPCODE,
                                  new BigDecimal(lineRecCounter),
                                  svaIndex,
                                  svaLineId);
            lineDetCounter++;
          } // end if svcitem opcode != noop
        } // end loop over svcItems
      } // end if svcItems != null
    } // end for loop over items
    setupLineCodes(numOfItems); //############# NEED TO ADJUST THIS FOR NEW lineRec INDEXING #############
  }

  /**
  *  Private helper method to set the iStore line codes; but only instantiates
  *  the Quote.lineCodes array if there is at least one non EMPTY_STRING value
  *
  */
  private void setupLineCodes(int numOfItems) throws FrameworkException, SQLException, ShoppingCartException {
    String METHOD = "setupLineCodes";
    int sciArrayLen = shopCartItems.length;
    ServiceItem [] svcItems = null; // placeholder for child "for" loops
    int sciCounter = 0; // counter thru the ShoppingCartItem array
    int svcCounter = 0; // counter thru the ServiceItem arrays (if any)
    int lineRecCounter = 0; // counter thru the LineRecord array
    int itemId;
    Item item;
    for (sciCounter = 0; sciCounter < sciArrayLen; sciCounter ++) {
      if (!ShoppingCartItem.NOOP_OPCODE.equals(shopCartItems[sciCounter].getOperationCode())) {
        if(!EMPTY_STRING.equals(shopCartItems[sciCounter].getShoppingCartLineCode())) {
          if (lineCodes == null) lineCodes = new BigDecimal[numOfItems];
          lineCodes[lineRecCounter] = new BigDecimal(shopCartItems[sciCounter].getShoppingCartLineCode());
          if(logEnabled) { IBEUtil.log(CLASS, "setupLineCodes", "linecode for shopCartItem[" + sciCounter + "]: (lineCode[" + lineRecCounter+ "] : " +lineCodes[lineRecCounter]);
}
        } else {
          try
          {
            itemId = Integer.parseInt(shopCartItems[sciCounter].getInventoryItemId());
            item = Item.load(itemId, Item.SHALLOW, false,false);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Item loaded");
}
            if(item.isModelBundle()) {
              if (lineCodes == null) lineCodes = new BigDecimal[numOfItems];
              lineCodes[lineRecCounter] = new BigDecimal(ShoppingCartItem.MODEL_BUNDLE_LINE_CODE);
              if(logEnabled) { IBEUtil.log(CLASS, "setupLineCodes", "linecode for shopCartItem[" + sciCounter + "]: (lineCode[" + lineRecCounter+ "] : " + lineCodes[lineRecCounter]);
}
            } else if (item.isModelItem()) {
              if (!item.isConfigurable()) throw new ShoppingCartException("IBE_CFG_ERROR_NO_UIDEF");
              if (lineCodes == null) lineCodes = new BigDecimal[numOfItems];
              lineCodes[lineRecCounter] = new BigDecimal(ShoppingCartItem.MODEL_UI_LINE_CODE);
              if(logEnabled) { IBEUtil.log(CLASS, "setupLineCodes", "linecode for shopCartItem[" + sciCounter + "]: (lineCode[" + lineRecCounter+ "] : " + lineCodes[lineRecCounter]);
}
            }
          }
          catch(ItemNotFoundException e)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Item not found!");
}
          }
        }
        lineRecCounter++;
      }
      if ((shopCartItems[sciCounter].svcItems != null) && (shopCartItems[sciCounter].svcItems.length >= 1)) {
        svcItems = shopCartItems[sciCounter].svcItems;
        for (svcCounter = 0; svcCounter < svcItems.length; svcCounter ++) {
          if (!ShoppingCartItem.NOOP_OPCODE.equals(svcItems[svcCounter].getOperationCode())) {
            if(!EMPTY_STRING.equals(svcItems[svcCounter].getShoppingCartLineCode())) {
              if (lineCodes == null) lineCodes = new BigDecimal[numOfItems];
              lineCodes[lineRecCounter] = new BigDecimal(svcItems[svcCounter].getShoppingCartLineCode());
              if(logEnabled) { IBEUtil.log(CLASS, "setupLineCodes", "linecode for svcItems[" + svcCounter + "]: (lineCode[" + lineRecCounter+ "] : " + lineCodes[lineRecCounter]);
}
            }
            lineRecCounter++;
          }
        }
      }
    } // end for loop

    if (lineCodes == null) {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "no line codes");
}
    } else {
      for (int i = 0; i < numOfItems; i++) {
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "lineRec[" + i + "].itemid: " + lineRec[i].inventory_item_id + " = lineCodes[" + i + "] : " + lineCodes[i]);
}
      }

    }
  }
  /**
   *  setupPromotionCodes - intended to setup the Quote.linePriceAttrRec
   *  for a save operation.
   *  <P>
   *  Currently, it will add codes based on the display value of the promo code only (and not the promo code id)
   *  via calls to PriceList.loadPromotions(String priceListName,int loadMode)
   *
   *  @return boolean representing whether we need to save linePriceAttributes
   */
  private boolean setupLinePromotionCodes()
  throws ShoppingCartException, FrameworkException, SQLException {
    String METHOD = "setupLinePromotionCodes";

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "START");

}
    if((shopCartItems == null) || (shopCartItems.length == 0))
      throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");

    int numItems = shopCartItems.length;
    int totalNumPromoCodes = 0;
    int i = 0; // shopCartItems counter
    int j = 0; // promoCode counter per item
    int k = 0; // linePricingAttr counter

    PromotionCode[] currPromoCodes = null;

    // loop over all items to count total number of promo codes & build an array of codes being used
    for (i = 0; i < numItems; i++) {
      currPromoCodes = shopCartItems[i].getPromotionCodes();
      if (currPromoCodes != null && currPromoCodes.length > 0) {
        totalNumPromoCodes = totalNumPromoCodes + currPromoCodes.length;
      }
    }
    if (totalNumPromoCodes == 0) {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "no promotion codes to set");
}
      return false;
    }

    linePriceAttrRec = new PriceAttributeRecord[totalNumPromoCodes];
    PriceList currPriceList = null;
    String currPromoName = "";

    // loop over all items & their promocodes, setting the line pricing attribute records accordingly
    for (i = 0; i < numItems; i++) {
      currPromoCodes = shopCartItems[i].getPromotionCodes();
      if (currPromoCodes != null && currPromoCodes.length > 0) {
        // loop over all promo codes for this item
        for (j = 0; j < currPromoCodes.length; j++) {
          currPromoName = currPromoCodes[j].getPromotionCodeName();
          if (EMPTY_STRING.equals(currPromoName)) {
            throw new ShoppingCartException("IBE_SC_CARTITEM_INCOMPLETE");
          }
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Calling: PriceList.loadPromotions for: " + currPromoName);
}
          currPriceList = PriceList.loadPromotions(currPromoName, PriceList.SHALLOW_LOAD);
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Back from Calling: PriceList.loadPromotions");
}
          if (currPriceList == null)
          {
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Got a null value returned from PriceList.loadPromotions; throwing exception for invalid promo code...");
}
            throw new ShoppingCartException("IBE_SC_INVALID_PROMOCODE");
          }
          linePriceAttrRec[k] = new PriceAttributeRecord();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " values for linePriceAttrRec["+k+"] : ");
}
          linePriceAttrRec[k].pricing_attribute1   = (currPriceList.header.list_header_id).toString();
          linePriceAttrRec[k].quote_header_id      = headerRec.quote_header_id;
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " -- quote_header_id   : " + linePriceAttrRec[k].quote_header_id);
}
          if (EMPTY_STRING.equals(shopCartItems[i].cartLineId)) {
            linePriceAttrRec[k].qte_line_index       = new BigDecimal(i+1);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, " -- qte_line_index    : " + linePriceAttrRec[k].qte_line_index);
}
          } else {
            linePriceAttrRec[k].quote_line_id        = new BigDecimal(shopCartItems[i].cartLineId);
            if(logEnabled) { IBEUtil.log(CLASS, METHOD, " -- quote_line_id     : " + linePriceAttrRec[k].quote_line_id );
}
          }
          linePriceAttrRec[k].pricing_context      = "MODLIST";
          linePriceAttrRec[k].flex_title           = "QP_ATTR_DEFNS_QUALIFIER";
          linePriceAttrRec[k].operation_code       = currPromoCodes[j].getOperationCode();
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " -- pricing_attribute1: " + linePriceAttrRec[k].pricing_attribute1);
          IBEUtil.log(CLASS, METHOD, " -- pricing_context   : " + linePriceAttrRec[k].pricing_context);
}
          if(logEnabled) { IBEUtil.log(CLASS, METHOD, " -- flex_title        : " + linePriceAttrRec[k].flex_title);
          IBEUtil.log(CLASS, METHOD, " -- operation_code    : " + linePriceAttrRec[k].operation_code);
}
          k++;
        } // end loop over promocodes for one item
      }
    } // end loop over items
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "END");
}
    if (k > 0) {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "returning true");
}
      return true;
    } else {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, "returning false");
}
      return false;
    }
  }

/*---------------------- NEW FUNCTIONS FOR NEW SAVE/SHARE FLOW -------------------------------*/
 /**
  * Renames the cart.<BR>
  * <BR>
  * <LI>Can deactivate the cart if necessary.
  * <LI>Does not reprice the cart.
  * <BR><BR>
  * Calls Quote.saveShareV2 with NAME_CART as the operation code, retrieval number if present in the cookie.  Please see the comments for Quote.saveShareV2 for more information.
  *
  * @param cartId quote header id of the cart to be activated
  * @param cartName name that the cart should be updated to
  * @param lastModifiedTimestamp last update date of the cart to be activated
  * @param deactivateCart boolean on whether to deactivate the cart or not
  * @throws FrameworkException if there is a framework layer error
  * @throws SQLException if there is a database error
  * @throws ShoppingCartException the error message will indicate the nature of the error
  */
  public static void updateCartName(String cartId, String cartName, String lastModifiedTimestamp, boolean deactivateCart)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    final String METHOD = "updateCartName";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: cartId                 : " + cartId);
    IBEUtil.log(CLASS, METHOD, "BEGIN: cartName               : " + cartName);
    IBEUtil.log(CLASS, METHOD, "BEGIN: lastModifiedTimestamp  : " + lastModifiedTimestamp);
    IBEUtil.log(CLASS, METHOD, "BEGIN: deactivateCart         : " + deactivateCart);
}
    ShoppingCart cart = new ShoppingCart();
    BigDecimal bigCartId = makeCartIdBigDecimal(cartId);
    cart.lastModifiedTimestamp = lastModifiedTimestamp;
    cart.setupHeaderRecord(bigCartId);
    cart.headerRec.quote_name = cartName;
    try {
      cart.saveShareV2(ShoppingCart.NAME_CART,
                    RequestCtx.getPartyId(),
                    RequestCtx.getAccountId(),
                    RequestCtx.getMinisiteId(),
                    RequestCtx.getShareeNumber(),
                    null,
                    null,
                    false,
                    deactivateCart,
                    0,
                    null);
    }
    catch(QuoteException e)
    {
      throw new ShoppingCartException("", e);
    }
  } // end updateCartName
  
  protected static BigDecimal makeCartIdBigDecimal(String cartId)
  throws ShoppingCartException
  {
    BigDecimal bigCartId = gMissNum;
    if (cartId == null)
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED");
    try {
      if (cartId.equals(EMPTY_STRING))
        cartId = gMissChar;
      else
        return bigCartId = new BigDecimal(cartId);
    }
    catch(NumberFormatException e)
    {
      if(IBEUtil.logEnabled()) { IBEUtil.log(CLASS, "makeCartIdBigDecimal", IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(IBEUtil.logEnabled()) { IBEUtil.log(CLASS, "makeCartIdBigDecimal", IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    return bigCartId;
  }

  protected static BigDecimal makeRetrievalNumBigDecimal(String retrievalNumber)
  throws ShoppingCartException
  {
    BigDecimal bigRetrievalNumber = null;
    if ((retrievalNumber == null) || retrievalNumber.equals(EMPTY_STRING)) return null;
    try {
      bigRetrievalNumber = new BigDecimal(retrievalNumber);
    }
    catch(NumberFormatException e)
    {
      if(IBEUtil.logEnabled()) { IBEUtil.log(CLASS, "makeCartIdBigDecimal", IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_SC_INVALID_RETRIEVAL_NUM", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(IBEUtil.logEnabled()) { IBEUtil.log(CLASS, "makeCartIdBigDecimal", IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_SC_INVALID_RETRIEVAL_NUM", e);
    }
    return bigRetrievalNumber;
  }

 /**
  * Adds the items from one cart into another.
  * <LI>Copies lines from the source cart into the target cart.
  * <LI>Reprices the target cart with the user's current settings.
  * <LI>Can delete the source cart if required.
  * <LI>Sends down recipient number if its in the cookie.
  * <LI>Only sends down the source lastModifiedTimestamp if the user is a member of a shared cart (based on the presence of a sharee number in the cookie).
  *
  * @param cartId The unique identifier of the source cart.
  * @param lastModifiedTimestamp Last modified timestamp of the source cart.
  * @param toCartId The Unique identifier of the target cart.
  * @param to_lastModifiedTimestamp Last modified timestamp of the target cart.
  * @param deleteSourceCart If true then source cart is deleted; If false then source cart is retained
  * @throws FrameworkException if there is a framework layer error.
  * @throws SQLException if there is a database error.
  * @throws ShoppingCartException the error message will indicate the nature of the error.
  * 
  * @rep:displayname Append to Saved Cart
  */
  public static void appendToSavedCart(String cartId, String lastModifiedTimestamp,
                                       String toCartId, String to_lastModifiedTimestamp, boolean deleteSourceCart)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    final String METHOD = "appendToSavedCart";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: cartId                  : " + cartId);
    IBEUtil.log(CLASS, METHOD, "BEGIN: lastModifiedTimestamp   : " + lastModifiedTimestamp);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: toCartId                : " + toCartId);
    IBEUtil.log(CLASS, METHOD, "BEGIN: to_lastModifiedTimestamp: " + to_lastModifiedTimestamp);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: deleteSourceCart        : " + deleteSourceCart);

}
    ShoppingCart cart = new ShoppingCart();
    BigDecimal bigCartId = makeCartIdBigDecimal(toCartId);
    cart.lastModifiedTimestamp = to_lastModifiedTimestamp;
    cart.setupHeaderRecord(bigCartId);
    cart.setupControlRecord(YES, YES, true); //For the bug 3258917
//    if (to_lastModifiedTimestamp!= null && !"".equals(to_lastModifiedTimestamp)) cart.headerRec.last_update_date = Timestamp.valueOf(to_lastModifiedTimestamp);
    Timestamp sourceLastUpdateDate = null;
    boolean isSharee = (RequestCtx.getShareeNumber() == null) ? false : true;
    if (isSharee) {
      if (lastModifiedTimestamp != null && !"".equals(lastModifiedTimestamp))  {
        sourceLastUpdateDate = Timestamp.valueOf(lastModifiedTimestamp);
      }
    }
    try {
      cart.saveShareV2(ShoppingCart.APPEND,
                    RequestCtx.getPartyId(),
                    RequestCtx.getAccountId(),
                    RequestCtx.getMinisiteId(),
                    RequestCtx.getShareeNumber(),
                    new BigDecimal(cartId),
                    sourceLastUpdateDate,
                    deleteSourceCart,
                    false,
                    0,
                    null);
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(QuoteException e)
    {
      throw new ShoppingCartException("", e);
    }
  } // end appendToSavedCart

 /** Calls other signature of activateCartForEdit with empty retrievalNumber */ 
  public static void activateCartForEdit(String cartId, String lastModifiedTimestamp, boolean reprice)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    ShoppingCart.activateCartForEdit(cartId, lastModifiedTimestamp, "", reprice);
  }
 /**
  * Activates the cart for update.
  * <LI>Marks the cart as the user's active one.
  * <LI>Can reprice the cart with the user's current settings.
  *
  * @param cartId The unique identifier of the cart to be activated.
  * @param lastModifiedTimestamp Last modified timestamp of the cart to be activated.
  * @param retrievalNumber The retrieval number if the member of a shared cart is activating a shared cart for update.
  * @param reprice The flag to indicate whether the cart needs to be repriced.
  * @throws FrameworkException if there is a framework layer error.
  * @throws SQLException if there is a database error.
  * @throws ShoppingCartException the error message will indicate the nature of the error.
  * @rep:displayname Activate Cart for Update
  */
  public static void activateCartForEdit(String cartId, String lastModifiedTimestamp, String retrievalNumber, boolean reprice)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    final String METHOD = "activateCartForEdit";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: cartId                 : " + cartId);
    IBEUtil.log(CLASS, METHOD, "BEGIN: lastModifiedTimestamp  : " + lastModifiedTimestamp);
}
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: retrievalNumber        : " + retrievalNumber);
    IBEUtil.log(CLASS, METHOD, "BEGIN: reprice                : " + reprice);
}
    ShoppingCart cart = new ShoppingCart();
    BigDecimal retrievalNumber_b = makeRetrievalNumBigDecimal(retrievalNumber);
    BigDecimal bigCartId = makeCartIdBigDecimal(cartId);
    cart.lastModifiedTimestamp = lastModifiedTimestamp;
    cart.setupHeaderRecord(bigCartId,retrievalNumber_b);
    if (reprice) cart.setupControlRecord(YES, YES, true); // calcTax, calcFreight, priceRecalc

    try {
      cart.saveShareV2(ShoppingCart.ACTIVATE_QUOTE,
                    RequestCtx.getPartyId(),
                    RequestCtx.getAccountId(),
                    RequestCtx.getMinisiteId(),
                    retrievalNumber_b,
                    null,
                    null,
                    false,
                    false,
                    0,
                    null);
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(QuoteException e)
    {
      throw new ShoppingCartException("", e);
    }
  } // end activateCartForEdit

 /**
  * Deactivates the cart.
  * <LI>Marks the cart so that it is no longer the user's active one.
  * <LI>Does not reprice the cart or do any other updates to it as it gets deactivated.
  *
  * @param cartId The unique identifier of the cart to be activated.
  * @param lastModifiedTimestamp last modified timestamp of the cart to be deactivated.
  * @throws FrameworkException If there is a framework layer error
  * @throws SQLException If there is a database error
  * @throws ShoppingCartException The error message will indicate the nature of the error
  * @rep:displayname Deactivate Cart
  */
  public static void deactivateCart(String cartId, String lastModifiedTimestamp)
  throws ShoppingCartException, SQLException, FrameworkException
  {
    final String METHOD = "deactivateCart";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: cartId                 : " + cartId);
    IBEUtil.log(CLASS, METHOD, "BEGIN: lastModifiedTimestamp  : " + lastModifiedTimestamp);
}
    ShoppingCart cart = new ShoppingCart();
    BigDecimal recipientNumber_b = null;
    BigDecimal bigCartId = makeCartIdBigDecimal(cartId);
    cart.lastModifiedTimestamp = lastModifiedTimestamp;
    cart.setupHeaderRecord(bigCartId);
    // do this assignment just in case setupHeaderRecord didn't for a sharee
    cart.headerRec.cust_account_id = RequestCtx.getAccountId();
    cart.headerRec.party_id = RequestCtx.getPartyId();

    try {
      cart.saveShareV2(ShoppingCart.DEACTIVATE,
                    RequestCtx.getPartyId(),
                    RequestCtx.getAccountId(),
                    RequestCtx.getMinisiteId(),
                    recipientNumber_b,
                    null,
                    null,
                    false,
                    true,
                    0,
                    null);
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(QuoteException e)
    {
      throw new ShoppingCartException("", e);
    }
  } // end deactivateCart

  /**
   * Loads all of the carts or published quotes belonging to a user that are not shared.
   * <LI>For each cart or published quote, only the header and Sold To information is loaded.
   * <LI>For published quotes, the isOrderable flag is set based on whether there is a status transition allowed into 'ORDER SUBMITTED'.
   *
   * @param partyId The unique identifier of the party being queried for.
   * @param accountId The unique identifier of the account being queried for.
   * @param loadType Either LOAD_CART or LOAD_QUOTE.
   * @return An array of {@link oracle.apps.ibe.shoppingcart.quote.ShoppingCart ShoppingCart} objects
   * @throws FrameworkException if there is a framework layer error
   * @throws SQLException if there is a database error
   * @throws ShoppingCartException the error message will indicate the nature of the error
   * @rep:displayname Load Unshared Carts
   */
  public static ShoppingCart[] loadUnShared(String partyId, String accountId, int loadType)
  throws ShoppingCartException, FrameworkException, SQLException
  {
    String METHOD = "loadUnShared";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: PartyId: " + partyId + " AccountId: " + accountId + " loadType: " + loadType);
}
    CartLoadControlRecord loadControlRec = new CartLoadControlRecord();
    loadControlRec.loadType = loadType;
    loadControlRec.shareType = UN_SHARED;
    return ShoppingCart.loadAllShareTypes(partyId, accountId, loadControlRec);
  }
  protected static ShoppingCart[] loadAllShareTypes(String partyId, String accountId, CartLoadControlRecord loadControlRec)
  throws ShoppingCartException, FrameworkException, SQLException
  {
    String METHOD = "loadAllShareTypes";
    boolean logEnabled = IBEUtil.logEnabled();  
    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "BEGIN: PartyId: " + partyId + " AccountId: " + accountId);
}
    BigDecimal bigPartyId = null;
    BigDecimal bigAccountId = null;
    try
    {
      bigPartyId = new BigDecimal(partyId);
      bigAccountId = new BigDecimal(accountId);
    }
    catch(NumberFormatException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }
    catch(StringIndexOutOfBoundsException e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD, IBEUtil.getStackTrace(e));
}
      throw new ShoppingCartException("IBE_INVALID_NUMBER_SPECIFIED", e);
    }

    Class           cartClass = null;
    ShoppingCart[]  scarts = null;
    QuoteLoadControlRecord quoteLoadCtrlRec = new QuoteLoadControlRecord();
    quoteLoadCtrlRec.loadType = loadControlRec.loadType;
    quoteLoadCtrlRec.shareType = loadControlRec.shareType;
    quoteLoadCtrlRec.loadContractCarts = true;
    try
    {
      if ((loadControlRec.shareType == SHARED_BY) || (loadControlRec.shareType == SHARED_TO)) {
        cartClass = Class.forName("oracle.apps.ibe.shoppingcart.quote.SharedCart");
      } else {
        cartClass = Class.forName("oracle.apps.ibe.shoppingcart.quote.ShoppingCart");
      }
    }
    catch(Exception e)
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,
                  " Could not get class def for ShoppingCart or SharedCart!!: "
                  + e.getMessage());
}
      return null;
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Abt to call Quote.loadAll");
}
    Quote[] allQuotes = loadAll(bigPartyId, bigAccountId, cartClass, quoteLoadCtrlRec);
    if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Done calling Quote.loadAll");

}
    if(allQuotes != null)
    {
      int allQuotesLength = allQuotes.length;
      int allCartsLength = 0;
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,"number returned from Quote.loadAll : " + allQuotesLength);
}
      if(allQuotesLength > 0)
      {
        int i = 0;
        for (i = 0; i < allQuotesLength; i++)
        {
          if (!"IBE_PRMT_SC_UNNAMED".equals(allQuotes[i].headerRec.quote_name))
            allCartsLength++;
        }
        if(logEnabled) { IBEUtil.log(CLASS, METHOD, "Num carts loaded is " + allCartsLength);
}
        scarts = new ShoppingCart[allCartsLength];
        int j = 0;
        for (i = 0; i < allQuotesLength; i++)
        {
          if (!"IBE_PRMT_SC_UNNAMED".equals(allQuotes[i].headerRec.quote_name))
          {
            // need to loop over scarts & "fill in" the string versions of all of them!!          
            scarts[j] = (ShoppingCart) allQuotes[i];
            scarts[j].fillHeaderInformation();
            scarts[j].fillSoldtoInformation();
            if (loadControlRec.loadType == LOAD_QUOTE) {
              QuoteStatus quoteStatus = QuoteStatus.load(allQuotes[i].headerRec.quote_status_id);
              scarts[j].isOrderable = quoteStatus.isTransitionAllowed("ORDER SUBMITTED");
            }
            j++;
          }
        }
//        System.arraycopy(allCarts, 0, scarts, 0, allCartsLength);
      }
      else
      {
        return null;
      }
    }
    else
    {
      if(logEnabled) { IBEUtil.log(CLASS, METHOD,"Quote.loadAll returned null");
}
      return null;
    }

    if(logEnabled) { IBEUtil.log(CLASS, METHOD, "DONE");
}
    return scarts;
  }

  public boolean isCartUnnamed()
  {
    if ("IBE_PRMT_SC_UNNAMED".equals(headerRec.quote_name))
      return true;
    else
      return false;
  }

  public boolean isCartUserNamed()
  {
    if ("IBE_PRMT_SC_UNNAMED".equals(headerRec.quote_name) || "IBE_PRMT_SC_DEFAULTNAMED".equals(headerRec.quote_name))
      return false;
    else
      return true;
  }

/**
   * Determines whether the cart is shared or not.
   *
   *
   * @return The flag to determine if the cart is shared or not.
   * @rep:displayname Is Cart Shared
   *
   */
  public boolean isCartShared()
  {
    return this.isShared;
  }
  public boolean isCartOrderable()
  {
    return this.isOrderable;
  }
  public void setLastModifiedTimeStamp(String lastModifiedTimestamp)
  {
    this.lastModifiedTimestamp = lastModifiedTimestamp;
  }
  private static String getRetrievalNumberString()
  {
    BigDecimal retrievalNumber = RequestCtx.getShareeNumber();
    String retreivalNumber_s = null;
    if (retrievalNumber != null) retreivalNumber_s = retrievalNumber.toString();
    return retreivalNumber_s;
  }

 
 /**
   *  clearHdrFFDataValues - Clears the flex field information set at header level.
   *  <P>
   *
   *  @return no parameter is returned.
   */

  public void clearHdrFFDataValues() throws FrameworkException, SQLException,
                                 QuoteException, ShoppingCartException
  {
     if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "Begin");
}
     BigDecimal  quoteHeaderId = new BigDecimal(cartId);
     if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "quoteHeaderId is " + quoteHeaderId);
}
     setupHeaderRecord(quoteHeaderId);
     if(headerRec == null)
     {
       headerRec = new HeaderRecord();
     }

     headerRec.attribute_category="";
     headerRec.attribute1="";
     headerRec.attribute2="";
     headerRec.attribute3="";
     headerRec.attribute4="";
     headerRec.attribute5="";
     headerRec.attribute6="";
     headerRec.attribute7="";
     headerRec.attribute8="";
     headerRec.attribute9="";
     headerRec.attribute10="";
     headerRec.attribute11="";
     headerRec.attribute12="";
     headerRec.attribute13="";
     headerRec.attribute14="";
     headerRec.attribute15="";
                                   
     try
     {
       if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "Calling Quote.save");
}
       save(RequestCtx.getPartyId(), RequestCtx.getAccountId(), RequestCtx.getShareeNumber(),
           Quote.SEPARATE_LINES, false,false, false, false, false, 
           false, false, false, false, false, false, false, false, 
           false, false, false, false, false);
       if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "Done Calling Quote.save");
}
      }
      catch(QuoteException e)
      {
        if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "Checking for timestamp problem");
}
        checkUpdateTimestamp(e);
      }
      if(logEnabled) { IBEUtil.log(CLASS, "clearHdrFFDataValues", "End");

}
  } //clearDFF
  
}

