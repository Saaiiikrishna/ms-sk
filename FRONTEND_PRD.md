# Product Requirements Document: Front-End Portals

## 1. Introduction

This document outlines the requirements for the various front-end portals of the MySillyDreams platform. These portals serve different user roles, including administrators, customers, inventory managers, vendors, support agents, and delivery personnel. The requirements are derived from the functionalities provided by the existing backend microservices (Auth Service, Catalog Service, User Service) and common e-commerce patterns.

## 2. Overarching Goals

*   Provide intuitive and efficient user interfaces for all user roles.
*   Ensure seamless interaction with backend services.
*   Maintain a consistent brand identity across all portals where appropriate.
*   Enable users to perform their tasks effectively and with satisfaction.

## 3. General UI Components (Common Across Portals)

*   **Navigation Bar/Header:** Logo, navigation links, user profile dropdown (login/logout, profile link), search bar (contextual).
*   **Footer:** Copyright, links (About Us, Contact, Terms).
*   **Login Form:** Username/email field, password field, OTP field (for MFA), submit button, links (forgot password, register).
*   **Registration Form:** Fields for user details (name, email, password, etc.), submit button.
*   **Data Table/List:** Display tabular data with columns, sorting, pagination, search/filter within the table, action buttons per row (edit, delete, view).
*   **Detail View:** Display information about a single item.
*   **Form:** Input fields, text areas, file uploads, submit/cancel buttons, validation messages.
*   **Search Bar:** Input field with a search button.
*   **Filter Controls:** Dropdowns, checkboxes, range sliders.
*   **Modals/Dialogs:** For confirmations, quick forms, alerts.
*   **Notifications/Toasts:** For success/error messages.
*   **Breadcrumbs:** Navigational aid.
*   **Dashboard Widgets/Cards:** Summarized visual information.
*   **Buttons:** Standard, primary, secondary, danger.
*   **Tabs:** Content organization.
*   **Accordions/Collapsible Panels:** Show/hide content sections.

---

## 4. Admin Panel

**Goal:** To provide administrators with comprehensive tools to manage users, content, operations, and system settings across the platform.

**Key Features/User Stories:**

*   As an admin, I want to manage all user accounts, roles, and permissions.
*   As an admin, I want to oversee and manage vendor onboarding and KYC processes.
*   As an admin, I want to manage the product catalog, categories, and pricing rules.
*   As an admin, I want to monitor inventory levels and adjustments.
*   As an admin, I want to oversee delivery operations and personnel.
*   As an admin, I want to manage support staff and have visibility into all support tickets.
*   As an admin, I want to configure system settings and perform administrative tasks like password rotation.
*   As an admin, I need to use MFA for enhanced security.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Secure admin authentication.
    *   Components: Login Form (with MFA).
*   **Dashboard:**
    *   Purpose: Overview of key platform metrics and quick navigation.
    *   Components: Dashboard Widgets, Quick Link Buttons/Cards.
*   **User Management:**
    *   **User List Page:**
        *   Purpose: View, search, and filter all platform users.
        *   Components: Data Table (users), Search Bar, Filter Controls (role, status), Create User Button.
    *   **User Detail/Edit Page:**
        *   Purpose: View and modify user details, assign roles, manage admin MFA.
        *   Components: Detail View (user info), Form (editing), Role Assignment Component, MFA Provisioning Button/QR Code Display.
    *   **Create User Page:** (Optional)
        *   Purpose: Allow admins to create new user accounts.
        *   Components: Form (user details).
*   **Vendor Management:**
    *   **Vendor List Page:**
        *   Purpose: View and manage all vendor accounts.
        *   Components: Data Table (vendors), Search Bar, Filter Controls (KYC status).
    *   **Vendor Detail Page:**
        *   Purpose: View vendor profile, manage KYC status and documents.
        *   Components: Detail View (vendor info), KYC Status Indicator, Document List, KYC Action Buttons.
*   **Inventory Management (Admin View):**
    *   **Inventory Item List Page:**
        *   Purpose: View all inventory items and their stock levels.
        *   Components: Data Table (items), Search Bar, Filter Controls.
    *   **Stock Adjustment Log Page:**
        *   Purpose: Review history of stock adjustments.
        *   Components: Data Table (log entries), Filter Controls (date, type).
*   **Delivery Management (Admin View):**
    *   **Delivery Personnel List Page:**
        *   Purpose: View and manage delivery personnel accounts.
        *   Components: Data Table (delivery users).
    *   **Delivery Assignment List Page:**
        *   Purpose: Oversee all delivery assignments.
        *   Components: Data Table (assignments), Filter Controls (status, personnel).
*   **Support Management (Admin View):**
    *   **Support Agent List Page:**
        *   Purpose: View and manage support agent accounts.
        *   Components: Data Table (support agents).
    *   **Support Ticket List Page:**
        *   Purpose: Oversee all support tickets, filter, and assign them.
        *   Components: Data Table (tickets), Filter Controls, Assign Ticket Button/Dropdown.
    *   **Support Ticket Detail Page:**
        *   Purpose: View complete details of a support ticket and manage it.
        *   Components: Detail View (ticket info), Message List/Thread View, Message Input Form, Status Update Dropdown, Assignment Dropdown.
*   **Catalog Management:**
    *   **Category List/Tree Page:**
        *   Purpose: Manage product categories.
        *   Components: Tree View or Nested List, Add/Edit/Delete Category Buttons/Forms.
    *   **Product List Page:**
        *   Purpose: Manage all products in the catalog.
        *   Components: Data Table (products), Search Bar, Filter Controls, Add Product Button.
    *   **Product Detail/Edit Page:**
        *   Purpose: Create or edit product details, pricing, and stock information.
        *   Components: Form (product fields), Pricing Input Fields, Stock Overview Display, Category Selector.
    *   **Price Rule Management Page:**
        *   Purpose: Manage bulk/volume discount rules.
        *   Components: Data Table (price rules), Add/Edit Rule Form.
*   **Settings/Configuration Page:**
    *   Purpose: Manage system-wide settings and access administrative tools.
    *   Components: Various Form elements, Log Viewer component.
*   **Password Rotation Tool Page:**
    *   Purpose: Allow admins to initiate password rotation for users.
    *   Components: Form (user selector, new password fields).
*   **My Profile (Admin):**
    *   Purpose: Allow admins to manage their own profile and MFA settings.
    *   Components: Detail View (profile info), Form (edit profile), MFA Setup Component.

---

## 5. Customer Portal

**Goal:** To provide a user-friendly e-commerce experience for customers to browse products, make purchases, manage their accounts, and seek support.

**Key Features/User Stories:**

*   As a customer, I want to easily register and log in to my account.
*   As a customer, I want to browse and search for products.
*   As a customer, I want to view detailed product information and add items to my cart.
*   As a customer, I want a simple and secure checkout process.
*   As a customer, I want to view my order history and track order status.
*   As a customer, I want to manage my profile, addresses, and payment methods.
*   As a customer, I want to create and track support tickets.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Customer authentication.
    *   Components: Login Form.
*   **Registration Page:**
    *   Purpose: New customer account creation.
    *   Components: Registration Form.
*   **Home Page/Dashboard:**
    *   Purpose: Welcome page with featured products, categories, and promotions.
    *   Components: Product Carousel/Grid, Category Links/Cards, Promotional Banners.
*   **Product Listing Page:**
    *   Purpose: Browse and filter products.
    *   Components: Product Grid/List (with product cards), Filter Controls, Sort Dropdown, Pagination.
*   **Product Detail Page:**
    *   Purpose: View detailed information about a specific product.
    *   Components: Image Gallery/Viewer, Product Information Section, Price Display, Quantity Selector, Add to Cart Button, Customer Reviews Section.
*   **Shopping Cart Page:**
    *   Purpose: Review and manage items selected for purchase.
    *   Components: Data Table (cart items), Quantity Updaters, Remove Item Buttons, Cart Summary, Proceed to Checkout Button.
*   **Checkout Process Pages:**
    *   **Shipping Information Page:**
        *   Purpose: Collect or confirm delivery address.
        *   Components: Address Form, Address Selector.
    *   **Payment Information Page:**
        *   Purpose: Collect payment details securely.
        *   Components: Payment Method Selector, Payment Form.
    *   **Order Review & Confirmation Page:**
        *   Purpose: Allow customers to review their order before final submission.
        *   Components: Order Summary List, Place Order Button.
*   **Order History Page:**
    *   Purpose: View a list of past orders.
    *   Components: Data Table (orders), View Order Button.
*   **Order Detail Page:**
    *   Purpose: View details of a specific past order.
    *   Components: Detail View (order info).
*   **User Profile Page:**
    *   Purpose: Manage personal information, addresses, and payment methods.
    *   Components: Tabs (Profile, Addresses, Payment Methods); Forms for each section.
*   **Support Ticket List Page (Customer):**
    *   Purpose: View own support tickets.
    *   Components: Data Table (tickets).
*   **Support Ticket Detail Page (Customer):**
    *   Purpose: View details and communicate on a specific support ticket.
    *   Components: Detail View (ticket info), Message List/Thread View, Message Input Form.
*   **Create Support Ticket Page:**
    *   Purpose: Allow customers to submit new support requests.
    *   Components: Form (subject, description, category).

---

## 6. Inventory Management Portal

**Goal:** To enable inventory managers to efficiently track and manage stock levels, process adjustments, and maintain accurate inventory records.

**Key Features/User Stories:**

*   As an inventory manager, I want to log in securely.
*   As an inventory manager, I want to view current stock levels for all items.
*   As an inventory manager, I want to record new stock arrivals (receive).
*   As an inventory manager, I want to record stock being issued (issue).
*   As an inventory manager, I want to make adjustments for discrepancies.
*   As an inventory manager, I want to view a log of all stock transactions.
*   As an inventory manager, I want to receive alerts for low stock items.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Inventory manager authentication.
    *   Components: Login Form.
*   **Dashboard:**
    *   Purpose: Overview of stock levels, alerts, and recent activity.
    *   Components: Dashboard Widgets (stock overview, low stock alerts).
*   **Inventory Item List Page:**
    *   Purpose: View, search, and filter all managed inventory items.
    *   Components: Data Table (items: SKU, name, quantity, reorder level), Search Bar, Filter Controls.
*   **Inventory Item Detail Page:** (Optional, could be a modal from list)
    *   Purpose: View detailed information about an item.
    *   Components: Detail View (item info).
*   **Stock Adjustment Page:**
    *   Purpose: Perform various stock adjustment operations.
    *   Components: Tabs (Receive, Issue, Adjust); Forms for each operation (Item Selector, Quantity Input, Reason).
*   **Stock Transaction Log Page:**
    *   Purpose: View history of all stock movements.
    *   Components: Data Table (transactions).
*   **My Profile (Inventory Manager):**
    *   Purpose: Manage own profile details.
    *   Components: Detail View, Form (for editing profile).

---

## 7. Vendor Portal

**Goal:** To provide vendors with tools to manage their profile, onboard, handle KYC requirements, and (potentially) manage their products and orders.

**Key Features/User Stories:**

*   As a vendor, I want to register and log in to the portal.
*   As a vendor, I want to manage my company profile information.
*   As a vendor, I want to upload and track the status of my KYC documents.
*   (If applicable) As a vendor, I want to list and manage my products.
*   (If applicable) As a vendor, I want to manage inventory and pricing for my products.
*   (If applicable) As a vendor, I want to view and process orders for my products.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Vendor authentication.
    *   Components: Login Form.
*   **Registration Page:**
    *   Purpose: New vendor sign-up and linking to a user account.
    *   Components: Registration Form (vendor-specific fields), User Account Linker.
*   **Dashboard:**
    *   Purpose: Overview of key vendor metrics (e.g., sales, KYC status).
    *   Components: Dashboard Widgets.
*   **My Profile/Vendor Profile Page:**
    *   Purpose: View and edit vendor information.
    *   Components: Form (vendor details).
*   **KYC Management Page:**
    *   Purpose: Upload KYC documents and track verification status.
    *   Components: Document Upload Component, List of Uploaded Documents, KYC Status Display.
*   **(If vendors manage products):**
    *   **Product Management Page:**
        *   Purpose: List, add, and edit products offered by the vendor.
        *   Components: Data Table (vendor's products), Add/Edit Product Buttons.
    *   **Product Create/Edit Form:**
        *   Purpose: Form to input/modify product details.
        *   Components: Form (product fields).
    *   **Inventory Management Page (Vendor):**
        *   Purpose: Manage stock for vendor's own products.
        *   Components: Data Table (vendor's product stock), Stock Update Forms.
    *   **Pricing Management Page (Vendor):**
        *   Purpose: Manage prices for vendor's products.
        *   Components: Form/Table for pricing.
*   **(If vendors fulfill orders):**
    *   **Order Management Page:**
        *   Purpose: View and manage orders related to vendor's products.
        *   Components: Data Table (orders), Update Status Buttons.
    *   **Order Detail Page (Vendor):**
        *   Purpose: View details of a specific order.
        *   Components: Detail View.

---

## 8. Support Panel

**Goal:** To equip support agents with the tools to efficiently manage customer inquiries, resolve issues, and provide excellent customer service.

**Key Features/User Stories:**

*   As a support agent, I want to log in securely.
*   As a support agent, I want to view and manage a queue of support tickets.
*   As a support agent, I want to access full ticket details, including customer information and history.
*   As a support agent, I want to communicate with customers and add internal notes.
*   As a support agent, I want to update ticket status, priority, and assignments.
*   As a support agent, I want to search for customers and view their support history.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Support agent authentication.
    *   Components: Login Form.
*   **Dashboard:**
    *   Purpose: Overview of ticket queues, agent performance, and key metrics.
    *   Components: Dashboard Widgets.
*   **Ticket Queue Page:**
    *   Purpose: View, filter, and manage all support tickets.
    *   Components: Data Table (tickets), Advanced Filter Controls, Bulk Action Controls.
*   **Ticket Detail Page:**
    *   Purpose: Handle individual support tickets.
    *   Components: Customer Info Panel, Ticket History/Thread, Message Input Form (rich text, internal notes), Status Update Dropdown, Priority Selector, Assign Agent Dropdown.
*   **Customer Search Page:**
    *   Purpose: Find specific customers.
    *   Components: Search Bar.
*   **Customer Detail Page (Support View):**
    *   Purpose: View customer profile and their entire support history.
    *   Components: Detail View (customer info), Data Table (customer's past tickets).
*   **My Profile (Support Agent):**
    *   Purpose: Manage own support agent profile.
    *   Components: Detail View, Form (for editing profile).
*   **(Optional) Knowledge Base/FAQ Management Page:**
    *   Purpose: Create and manage help articles.
    *   Components: Rich Text Editor, List of Articles, Category Management.

---

## 9. Delivery Portal

**Goal:** To provide delivery personnel with a mobile-friendly interface to manage their assignments, track deliveries, and update statuses in real-time.

**Key Features/User Stories:**

*   As a delivery person, I want to log in easily, preferably on a mobile device.
*   As a delivery person, I want to see my current list of delivery assignments.
*   As a delivery person, I want to view details for each assignment (order, customer, address).
*   As a delivery person, I want to update the status of my delivery (e.g., arrived, call made).
*   As a delivery person, I want to upload proof of delivery (e.g., photo).
*   As a delivery person, I want to verify delivery with an OTP if required.
*   As a delivery person, I want to mark assignments as completed.
*   As a delivery person, I want to view my delivery history.

**Pages & Components:**

*   **Login Page:**
    *   Purpose: Delivery personnel authentication.
    *   Components: Login Form.
*   **Dashboard/Current Assignments Page:**
    *   Purpose: Display active and pending delivery assignments.
    *   Components: List/Card View of Assignments, Map View Component (optional).
*   **Assignment Detail Page:**
    *   Purpose: Provide all necessary information for a specific delivery.
    *   Components: Detail View (order, customer, notes), Action Buttons (Arrived, Called, Navigate), Link to Photo Upload, Link to OTP Verification.
*   **Photo Upload Page/Modal:**
    *   Purpose: Capture and upload proof of delivery.
    *   Components: File Upload Component (camera access), Image Preview.
*   **OTP Verification Page/Modal:**
    *   Purpose: Securely verify delivery with the customer.
    *   Components: OTP Input Field, Submit Button.
*   **Delivery History Page:**
    *   Purpose: View a log of past deliveries.
    *   Components: Data Table (past deliveries).
*   **My Profile (Delivery Personnel):**
    *   Purpose: Manage own profile information (e.g., vehicle details).
    *   Components: Detail View, Form (for editing profile).

---

## 10. Non-Functional Requirements (General)

*   **Usability:** Portals should be intuitive and easy to navigate for their target users.
*   **Performance:** Pages should load quickly, and interactions should be responsive.
*   **Responsiveness:** Customer Portal and Delivery Portal must be highly responsive and mobile-friendly. Other portals should be usable on tablet and desktop.
*   **Security:** All portals must enforce authentication and authorization based on user roles. Sensitive data display should be handled according to PII protection policies. Input validation is critical.
*   **Accessibility:** Strive for WCAG AA compliance where feasible.
*   **Scalability:** Front-end architecture should be able to handle a growing number of users and data.

## 11. Future Considerations

*   Real-time notifications (e.g., new order for vendor, new ticket for support).
*   Internationalization and localization.
*   Advanced analytics and reporting dashboards.
*   Integration with live chat for customer support.

This PRD provides a foundational outline. Each portal and its features would benefit from further detailed design, UX research, and iterative development.
